package tech.kayys.cifr.engine.tool.sandbox;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.exec.ExecutionResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Local process runner. This class is not an OS/container security sandbox:
 * network, filesystem write-path and memory restrictions require an isolated
 * process backend and are not enforceable by {@link ProcessBuilder}.
 */
@ApplicationScoped
public class DefaultExecutionSandbox implements ExecutionSandbox {

    private static final int MAX_CAPTURE_BYTES_PER_STREAM = 1024 * 1024;
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    @Override
    public Uni<ExecutionResult> execute(List<String> command, SandboxPolicy policy) {
        return Uni.createFrom().item(() -> executeBlocking(command, policy))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private ExecutionResult executeBlocking(List<String> command, SandboxPolicy policy) {
        Process process = null;
        CompletableFuture<String> stdoutFuture = null;
        CompletableFuture<String> stderrFuture = null;
        try {
            validate(command, policy);

            ProcessBuilder builder = new ProcessBuilder(command);
            if (policy != null && policy.workDir() != null) {
                builder.directory(policy.workDir().toAbsolutePath().normalize().toFile());
            }
            builder.redirectErrorStream(false);
            process = builder.start();

            // Close stdin so commands waiting for input do not hang indefinitely.
            process.getOutputStream().close();

            // Drain both pipes concurrently before waiting; waiting first can deadlock
            // when a child fills either OS pipe buffer.
            stdoutFuture = captureAsync(process.getInputStream());
            stderrFuture = captureAsync(process.getErrorStream());

            long timeoutMillis = timeoutMillis(policy);
            boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            }

            String stdout = getCaptured(stdoutFuture);
            String stderr = getCaptured(stderrFuture);
            if (!completed) {
                return new ExecutionResult(-1, stdout, append(stderr, "Process timed out after " + timeoutMillis + " ms"), true);
            }
            return new ExecutionResult(process.exitValue(), stdout, stderr, false);
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return new ExecutionResult(-1, "", "Process execution interrupted", false);
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ExecutionResult(-1, "", message, false);
        } finally {
            if (process != null) {
                try { process.getInputStream().close(); } catch (IOException ignored) {}
                try { process.getErrorStream().close(); } catch (IOException ignored) {}
                try { process.getOutputStream().close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void validate(List<String> command, SandboxPolicy policy) {
        if (command == null || command.isEmpty() || command.get(0) == null || command.get(0).isBlank()) {
            throw new IllegalArgumentException("Command must contain a non-blank executable");
        }
        if (command.stream().anyMatch(argument -> argument == null)) {
            throw new IllegalArgumentException("Command arguments must not contain null values");
        }
        if (policy != null && policy.workDir() != null && !Files.isDirectory(policy.workDir())) {
            throw new IllegalArgumentException("Working directory does not exist: " + policy.workDir());
        }
        if (policy != null && policy.allowedBinaries() != null && !policy.allowedBinaries().isEmpty()
                && !policy.allowedBinaries().contains(command.get(0))) {
            throw new SecurityException("Executable is not in the configured allowlist: " + command.get(0));
        }
        timeoutMillis(policy);
    }

    private static long timeoutMillis(SandboxPolicy policy) {
        Duration timeout = policy != null ? policy.timeout() : null;
        if (timeout == null) return DEFAULT_TIMEOUT_MILLIS;
        long millis = timeout.toMillis();
        if (millis <= 0) throw new IllegalArgumentException("Process timeout must be positive");
        return millis;
    }

    private static CompletableFuture<String> captureAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return capture(stream);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private static String capture(InputStream stream) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int remaining = MAX_CAPTURE_BYTES_PER_STREAM;
        boolean truncated = false;
        int read;
        while ((read = stream.read(buffer)) != -1) {
            int keep = Math.min(read, remaining);
            if (keep > 0) {
                captured.write(buffer, 0, keep);
                remaining -= keep;
            }
            if (keep < read) truncated = true;
        }
        String output = captured.toString(StandardCharsets.UTF_8);
        return truncated ? append(output, "[output truncated after " + MAX_CAPTURE_BYTES_PER_STREAM + " bytes]") : output;
    }

    private static String getCaptured(CompletableFuture<String> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static String append(String output, String suffix) {
        if (output == null || output.isEmpty()) return suffix;
        return output + System.lineSeparator() + suffix;
    }
}
