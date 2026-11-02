package tech.kayys.cifr.engine.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.provider.InferenceRequestAdapter;
import tech.kayys.cifr.engine.provider.ProviderRegistry;
import tech.kayys.gollek.grpc.GenerateRequest;
import tech.kayys.gollek.grpc.GenerateResponse;
import tech.kayys.gollek.grpc.InferenceServiceGrpc;
import tech.kayys.andalus.extension.Id;
import tech.kayys.andalus.extension.Metadata;
import tech.kayys.andalus.identity.ResourceId;
import tech.kayys.andalus.inference.Choice;
import tech.kayys.andalus.inference.CompletionRequest;
import tech.kayys.andalus.inference.CompletionResult;
import tech.kayys.andalus.inference.CompletionStream;
import tech.kayys.andalus.inference.InferenceProvider;
import tech.kayys.andalus.inference.Message;
import tech.kayys.andalus.inference.MessageRole;
import tech.kayys.andalus.inference.ModelInfo;
import tech.kayys.andalus.inference.Usage;
import tech.kayys.andalus.resource.ResourceType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Primary {@link InferenceProvider} for the Cifr coding agent.
 *
 * <h3>Routing strategy (in priority order)</h3>
 * <ol>
 *   <li><strong>Gollek gRPC :9131</strong> — lowest latency, binary framing, preferred.</li>
 *   <li><strong>Gollek REST :8080 / :11434</strong> — OpenAI-compatible HTTP fallback.</li>
 *   <li><strong>Cloud provider via {@link ProviderRegistry}</strong> — delegates to registered
 *       {@link InferenceProvider} plugins (e.g. {@code gemini}, {@code openai}, {@code anthropic}).</li>
 *   <li><strong>Static fallback</strong> — returns a polite prompt when no backend is reachable.</li>
 * </ol>
 */
@ApplicationScoped
public class DefaultInferenceBackend implements InferenceProvider {

    private static final Logger LOG = Logger.getLogger(DefaultInferenceBackend.class.getName());
    private static final String PROVIDER_ID = "gollek";
    private static final String VERSION = "0.1.0";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject
    ProviderRegistry providerRegistry;

    private final ResourceId stableId = ResourceId.from(
            Id.fromUUID(UUID.nameUUIDFromBytes(PROVIDER_ID.getBytes(StandardCharsets.UTF_8))),
            new ResourceType.Plugin()
    );

    // ── InferenceProvider & Extension identity ─────────────────────────────────

    @Override public ResourceId id()          { return stableId; }
    @Override public String getId()           { return PROVIDER_ID; }
    @Override public ResourceType type()      { return new ResourceType.Plugin(); }
    @Override public boolean supportsStreaming() { return true; }

    @Override
    public Metadata metadata() {
        return Metadata.builder()
                .name("Gollek Inference Backend")
                .description("Primary inference routing provider (gRPC / local REST / cloud fallback)")
                .version(VERSION)
                .build();
    }

    @Override
    public void initialize() throws Exception {
        LOG.info("DefaultInferenceBackend initialized.");
    }

    private static final long MODEL_CACHE_TTL_MS = 10_000L;
    private long lastModelFetchTime = 0;
    private final Map<String, ModelInfo> cachedModelInfos = new LinkedHashMap<>();

    private String resolveGollekCli() {
        String envCli = System.getenv("GOLLEK_CLI");
        if (envCli != null && java.nio.file.Files.isExecutable(java.nio.file.Path.of(envCli))) {
            return envCli;
        }
        String home = System.getProperty("user.home");
        if (home != null) {
            java.nio.file.Path localBin = java.nio.file.Path.of(home, ".local", "bin", "gollek");
            if (java.nio.file.Files.isExecutable(localBin)) return localBin.toString();
            java.nio.file.Path cargoBin = java.nio.file.Path.of(home, ".cargo", "bin", "gollek");
            if (java.nio.file.Files.isExecutable(cargoBin)) return cargoBin.toString();
        }
        if (java.nio.file.Files.isExecutable(java.nio.file.Path.of("/usr/local/bin/gollek"))) {
            return "/usr/local/bin/gollek";
        }
        return "gollek";
    }

    private synchronized void refreshGollekModels() {
        long now = System.currentTimeMillis();
        if (now - lastModelFetchTime < MODEL_CACHE_TTL_MS && !cachedModelInfos.isEmpty()) {
            return;
        }
        cachedModelInfos.clear();
        try {
            String gollekCli = resolveGollekCli();
            ProcessBuilder pb = new ProcessBuilder(gollekCli, "list", "-f=json");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        String id = node.hasNonNull("id") ? node.get("id").asText() : "";
                        String name = node.hasNonNull("name") ? node.get("name").asText() : id;
                        String format = node.hasNonNull("format") ? node.get("format").asText() : "gguf";
                        String taskType = node.hasNonNull("taskType") ? node.get("taskType").asText() : "text";
                        long size = node.hasNonNull("size") ? node.get("size").asLong() : 0L;

                        if (!id.isBlank()) {
                            ModelInfo info = new ModelInfo(
                                    id,
                                    name,
                                    format,
                                    Set.of("chat", "streaming", taskType),
                                    Map.of("sizeBytes", size, "format", format, "taskType", taskType)
                            );
                            cachedModelInfos.put(id, info);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.fine("Could not fetch models from gollek CLI: " + e.getMessage());
        }

        if (cachedModelInfos.isEmpty()) {
            cachedModelInfos.put("hf:unsloth/gemma-4-12b-it-GGUF", new ModelInfo("hf:unsloth/gemma-4-12b-it-GGUF", "gemma-4-12b-it-GGUF", "gguf", Set.of("chat"), Map.of()));
            cachedModelInfos.put("gemini-2.5-flash", new ModelInfo("gemini-2.5-flash", "gemini-2.5-flash", "cloud", Set.of("chat"), Map.of()));
            cachedModelInfos.put("gemini-2.5-pro", new ModelInfo("gemini-2.5-pro", "gemini-2.5-pro", "cloud", Set.of("chat"), Map.of()));
            cachedModelInfos.put("gpt-4o", new ModelInfo("gpt-4o", "gpt-4o", "cloud", Set.of("chat"), Map.of()));
            cachedModelInfos.put("claude-3-5-sonnet", new ModelInfo("claude-3-5-sonnet", "claude-3-5-sonnet", "cloud", Set.of("chat"), Map.of()));
        }
        lastModelFetchTime = now;
    }

    @Override
    public Set<String> listModels() {
        refreshGollekModels();
        return Collections.unmodifiableSet(cachedModelInfos.keySet());
    }

    @Override
    public ModelInfo getModelInfo(String modelId) {
        refreshGollekModels();
        ModelInfo info = cachedModelInfos.get(modelId);
        if (info != null) return info;
        return new ModelInfo(modelId, modelId, "gguf", Set.of("chat", "streaming"), Map.of(
                "maxContextTokens", 128000,
                "maxOutputTokens", 8192
        ));
    }

    // ── InferenceProvider generate & stream ────────────────────────────────────

    @Override
    public CompletionResult generate(CompletionRequest request) throws Exception {
        String reqId = request.id() != null ? request.id() : UUID.randomUUID().toString();
        String model = resolveModel(request);

        // 1. Gollek gRPC :9131 — primary
        CompletionResult grpcResp = tryGrpcGenerate(reqId, request, model);
        if (grpcResp != null) return grpcResp;

        // 2. Gollek REST :8080 / :11434 — local HTTP fallback
        CompletionResult localResp = tryLocalOpenAiGenerate(reqId, request, model);
        if (localResp != null) return localResp;

        // 3. Cloud provider via ProviderRegistry
        CompletionResult cloudResp = tryCloudProviderGenerate(reqId, model, request);
        if (cloudResp != null) return cloudResp;

        // 4. Fallback text
        return CompletionResult.of("I have inspected the workspace. What would you like to explore, build, or modify?");
    }

    @Override
    public CompletionStream stream(CompletionRequest request) throws Exception {
        String reqId = request.id() != null ? request.id() : UUID.randomUUID().toString();
        String model = resolveModel(request);

        // 1. Try gRPC streaming :9131
        CompletionStream grpcStream = tryGrpcStream(reqId, request, model);
        if (grpcStream != null) return grpcStream;

        // 2. Fall back to generate() and stream token/word chunks
        CompletionResult result = generate(request);
        String text = result.getContent() != null ? result.getContent() : "";

        List<String> words = new ArrayList<>();
        for (String w : text.split("(?<=\\s)|(?<=\\n)")) {
            if (!w.isEmpty()) words.add(w);
        }

        final Iterator<String> wordIter = words.iterator();
        return new CompletionStream() {
            private boolean closed = false;

            @Override public boolean hasNext() { return !closed && wordIter.hasNext(); }
            @Override public CompletionResult next() { return CompletionResult.of(wordIter.next()); }
            @Override public void close() { closed = true; }
            @Override public boolean isComplete() { return closed || !wordIter.hasNext(); }
            @Override public String getStreamId() { return reqId; }
        };
    }

    // ── Transport implementations ──────────────────────────────────────────────

    private CompletionResult tryGrpcGenerate(String reqId, CompletionRequest request, String model) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("localhost", 9131)
                    .usePlaintext()
                    .build();
            InferenceServiceGrpc.InferenceServiceBlockingStub stub =
                    InferenceServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(45, TimeUnit.SECONDS);

            GenerateRequest grpcReq = GenerateRequest.newBuilder()
                    .setPrompt(buildPromptFromMessages(request.messages()))
                    .setModel(model)
                    .setMaxTokens(request.maxTokens() > 0 ? request.maxTokens() : 2048)
                    .setTemperature((float) request.temperature())
                    .build();

            GenerateResponse grpcResp = stub.generate(grpcReq);
            String text = grpcResp.getText();
            if (text != null && !text.isBlank()) {
                return CompletionResult.of(text);
            }
        } catch (Exception e) {
            LOG.fine("gRPC inference unavailable: " + e.getMessage());
        } finally {
            if (channel != null) {
                try { channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private CompletionStream tryGrpcStream(String reqId, CompletionRequest request, String model) {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("localhost", 9131)
                    .usePlaintext()
                    .build();
            InferenceServiceGrpc.InferenceServiceBlockingStub stub =
                    InferenceServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(60, TimeUnit.SECONDS);

            GenerateRequest grpcReq = GenerateRequest.newBuilder()
                    .setPrompt(buildPromptFromMessages(request.messages()))
                    .setModel(model)
                    .setMaxTokens(request.maxTokens() > 0 ? request.maxTokens() : 2048)
                    .setTemperature((float) request.temperature())
                    .build();

            Iterator<GenerateResponse> streamIter = stub.generateStream(grpcReq);
            if (!streamIter.hasNext()) {
                channel.shutdownNow();
                return null;
            }

            final ManagedChannel ch = channel;
            return new CompletionStream() {
                private boolean done = false;

                @Override
                public boolean hasNext() {
                    if (done) return false;
                    try {
                        return streamIter.hasNext();
                    } catch (Exception e) {
                        done = true;
                        return false;
                    }
                }

                @Override
                public CompletionResult next() {
                    GenerateResponse chunk = streamIter.next();
                    String text = chunk.getText() != null ? chunk.getText() : "";
                    return CompletionResult.of(text);
                }

                @Override
                public void close() {
                    done = true;
                    try { ch.shutdownNow().awaitTermination(1, TimeUnit.SECONDS); } catch (Exception ignored) {}
                }

                @Override
                public boolean isComplete() {
                    return done;
                }

                @Override
                public String getStreamId() {
                    return reqId;
                }
            };
        } catch (Exception e) {
            LOG.fine("gRPC stream unavailable: " + e.getMessage());
            if (channel != null) {
                try { channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private CompletionResult tryLocalOpenAiGenerate(String reqId, CompletionRequest request, String model) {
        for (String endpoint : List.of(
                "http://localhost:8080/v1/chat/completions",
                "http://localhost:11434/v1/chat/completions")) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model);
                body.put("stream", false);

                List<Map<String, String>> msgs = new ArrayList<>();
                if (request.messages() != null) {
                    for (Message m : request.messages()) {
                        String role = m.role() != null ? m.role().toString().toLowerCase() : "user";
                        msgs.add(Map.of("role", role, "content", m.content() != null ? m.content() : ""));
                    }
                }
                body.put("messages", msgs);

                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer community")
                        .header("X-API-Key", "community")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .timeout(Duration.ofSeconds(45))
                        .build();

                HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String content = mapper.readTree(resp.body())
                            .path("choices").path(0).path("message").path("content").asText("");
                    if (!content.isBlank()) {
                        return CompletionResult.of(content);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private CompletionResult tryCloudProviderGenerate(String reqId, String model, CompletionRequest request) {
        String providerId = null;
        if (request.metadata() != null) {
            Object hint = request.metadata().get("providerId");
            if (hint instanceof String s && !s.isBlank()) providerId = s;
        }

        InferenceProvider provider = providerId != null
                ? providerRegistry.find(providerId).orElse(null)
                : providerRegistry.all().stream()
                        .filter(p -> p != this)
                        .findFirst()
                        .orElse(null);

        if (provider == null) {
            LOG.fine("No cloud InferenceProvider available.");
            return null;
        }

        try {
            return provider.generate(request);
        } catch (Exception e) {
            LOG.warning("Cloud provider '" + provider.id() + "' error: " + e.getMessage());
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String resolveModel(CompletionRequest request) {
        return (request.model() != null && !request.model().isBlank())
                ? request.model() : "hf:unsloth/gemma-4-12b-it-GGUF";
    }

    private String buildPromptFromMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String role = m.role() != null ? m.role().toString() : "USER";
            sb.append(role).append(": ").append(m.content() != null ? m.content() : "").append("\n");
        }
        sb.append("ASSISTANT:");
        return sb.toString();
    }
}
