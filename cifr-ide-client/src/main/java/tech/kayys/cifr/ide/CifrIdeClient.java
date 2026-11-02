package tech.kayys.cifr.ide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Small process-backed client for CIFR IDE's protocol-v2 JSON-RPC stdio host.
 *
 * <p>This class deliberately owns transport only. Workspace/document state and IDE
 * operations remain authoritative in CIFR IDE; coding plans, policy and patch
 * authorization remain in CIFR.</p>
 */
public final class CifrIdeClient implements AutoCloseable {
    public static final int REQUIRED_PROTOCOL_VERSION = 2;
    private static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;

    private final Process process;
    private final InputStream input;
    private final OutputStream output;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;
    private final Consumer<JsonNode> notificationHandler;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private volatile boolean closed;
    private volatile JsonNode serverInfo;

    private CifrIdeClient(Process process, Duration requestTimeout,
                          Consumer<JsonNode> notificationHandler) {
        this.process = process;
        this.input = new BufferedInputStream(process.getInputStream());
        this.output = new BufferedOutputStream(process.getOutputStream());
        this.mapper = new ObjectMapper();
        this.requestTimeout = requestTimeout;
        this.notificationHandler = notificationHandler == null ? ignored -> { } : notificationHandler;
        Thread reader = new Thread(this::readLoop, "cifr-ide-jsonrpc-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Starts the executable directly (without a shell), negotiates protocol v2,
     * and verifies that the host exposes its command catalogue.
     */
    public static CifrIdeClient start(List<String> command, Path workingDirectory,
                                     Duration requestTimeout,
                                     Consumer<JsonNode> notificationHandler)
            throws IOException, InterruptedException, TimeoutException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("CIFR IDE server command must not be empty");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        if (workingDirectory != null) builder.directory(workingDirectory.toFile());
        // stdout is reserved for framed JSON-RPC; diagnostics belong on stderr.
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        CifrIdeClient client = new CifrIdeClient(process, requestTimeout, notificationHandler);
        try {
            JsonNode info = client.request("initialize", Map.of("version", REQUIRED_PROTOCOL_VERSION));
            int protocol = info.path("protocol").asInt(-1);
            if (protocol != REQUIRED_PROTOCOL_VERSION) {
                throw new IOException("Unsupported CIFR IDE protocol " + protocol
                        + "; required " + REQUIRED_PROTOCOL_VERSION);
            }
            client.serverInfo = info;
            client.request("commands", Map.of());
            return client;
        } catch (IOException | InterruptedException | TimeoutException | RuntimeException e) {
            client.close();
            throw e;
        }
    }

    public JsonNode serverInfo() {
        return serverInfo == null ? null : serverInfo.deepCopy();
    }

    /** Returns a snapshot of the commands advertised by the running host. */
    public Set<String> commands() throws IOException, InterruptedException, TimeoutException {
        JsonNode result = request("commands", Map.of());
        if (!result.isArray()) throw new IOException("CIFR IDE commands response is not an array");
        Set<String> names = new LinkedHashSet<>();
        result.forEach(value -> {
            if (value.isTextual()) names.add(value.asText());
        });
        return Set.copyOf(names);
    }

    /** Invokes one IDE host command in the caller's stable window identity. */
    public JsonNode invoke(String window, String command, JsonNode arguments)
            throws IOException, InterruptedException, TimeoutException {
        if (window == null || window.isBlank()) throw new IllegalArgumentException("window is required");
        if (command == null || command.isBlank()) throw new IllegalArgumentException("command is required");
        ObjectNode params = mapper.createObjectNode();
        params.put("window", window);
        params.put("cmd", command);
        params.set("args", arguments == null ? mapper.createObjectNode() : arguments);
        return request("invoke", params);
    }

    public JsonNode invoke(String window, String command, Map<String, ?> arguments)
            throws IOException, InterruptedException, TimeoutException {
        return invoke(window, command, mapper.valueToTree(arguments == null ? Map.of() : arguments));
    }

    private JsonNode request(String method, Object params)
            throws IOException, InterruptedException, TimeoutException {
        if (closed || !process.isAlive()) throw new IOException("CIFR IDE server is not running");
        long id = nextId.getAndIncrement();
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pending.put(id, response);
        ObjectNode message = mapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("method", method);
        message.set("params", mapper.valueToTree(params));
        try {
            writeFrame(mapper.writeValueAsBytes(message));
            return response.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("CIFR IDE request failed: " + method, cause);
        } finally {
            pending.remove(id);
        }
    }

    private void writeFrame(byte[] body) throws IOException {
        if (body.length > MAX_FRAME_BYTES) throw new IOException("JSON-RPC frame exceeds limit");
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        synchronized (writeLock) {
            if (closed) throw new IOException("CIFR IDE client is closed");
            output.write(header);
            output.write(body);
            output.flush();
        }
    }

    private void readLoop() {
        try {
            while (!closed) {
                byte[] body = readFrame(input);
                JsonNode message = mapper.readTree(body);
                if (message.has("method")) {
                    try {
                        notificationHandler.accept(message);
                    } catch (RuntimeException ignored) {
                        // A consumer callback must not terminate the transport reader.
                    }
                    continue;
                }
                JsonNode idNode = message.get("id");
                if (idNode == null || !idNode.canConvertToLong()) continue;
                CompletableFuture<JsonNode> future = pending.get(idNode.asLong());
                if (future == null) continue;
                JsonNode error = message.get("error");
                if (error != null && !error.isNull()) {
                    future.completeExceptionally(new IOException("CIFR IDE RPC error: "
                            + error.path("code").asText("unknown") + " "
                            + error.path("message").asText("unspecified error")));
                } else {
                    future.complete(message.path("result"));
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!closed) failPending(new IOException("CIFR IDE server connection ended", e));
        } finally {
            if (!closed) failPending(new EOFException("CIFR IDE server closed stdout"));
        }
    }

    private static byte[] readFrame(InputStream input) throws IOException {
        StringBuilder headers = new StringBuilder();
        int matched = 0;
        while (matched < 4) {
            int next = input.read();
            if (next < 0) throw new EOFException("CIFR IDE server closed the protocol stream");
            headers.append((char) next);
            if ((matched == 0 || matched == 2) && next == '\r') matched++;
            else if ((matched == 1 || matched == 3) && next == '\n') matched++;
            else matched = next == '\r' ? 1 : 0;
            if (headers.length() > 8192) throw new IOException("JSON-RPC headers exceed limit");
        }
        int length = -1;
        for (String line : headers.toString().split("\\r\\n")) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                try {
                    length = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid Content-Length header", e);
                }
            }
        }
        if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("Invalid JSON-RPC frame length: " + length);
        byte[] body = input.readNBytes(length);
        if (body.length != length) throw new EOFException("Truncated JSON-RPC frame");
        return body;
    }

    private void failPending(IOException failure) {
        pending.values().forEach(future -> future.completeExceptionally(failure));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        failPending(new IOException("CIFR IDE client closed"));
        try { output.close(); } catch (IOException ignored) { }
        try { input.close(); } catch (IOException ignored) { }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
