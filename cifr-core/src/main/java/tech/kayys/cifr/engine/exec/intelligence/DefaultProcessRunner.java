package tech.kayys.cifr.engine.exec.intelligence;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared process runner. Process work and stream draining run away from the
 * caller thread; cancellation and timeout terminate the complete descendant tree.
 */
@ApplicationScoped
public class DefaultProcessRunner implements ProcessRunner {

    private static final Logger LOG = Logger.getLogger(DefaultProcessRunner.class);
    private final Map<String, RunningProcess> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public Uni<ProcessResult> execute(ProcessSpec spec) {
        return Uni.createFrom().completionStage(() ->
                CompletableFuture.supplyAsync(() -> executeBlocking(spec)));
    }

    private ProcessResult executeBlocking(ProcessSpec spec) {
        if (spec == null || spec.command().isEmpty()
                || spec.command().get(0) == null || spec.command().get(0).isBlank()) {
            return result(-1, Duration.ZERO, ProcessStatus.START_FAILED, "", "No command specified", null);
        }

        Instant start = Instant.now();
        ExecutionBudget budget = spec.budget() != null ? spec.budget() : ExecutionBudget.defaultBudget();
        Process process = null;
        String processId = null;
        RunningProcess running = null;

        try {
            ProcessBuilder builder = new ProcessBuilder(spec.command());
            if (spec.workingDirectory() != null) {
                builder.directory(spec.workingDirectory().toFile());
            }
            builder.environment().putAll(spec.environment());

            process = builder.start();
            processId = spec.executionId();
            final Process startedProcess = process;
            running = new RunningProcess(startedProcess);
            activeProcesses.put(processId, running);

            long outputLimit = Math.max(0L, budget.maxOutputBytes());
            AtomicLong capturedBytes = new AtomicLong();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                    () -> readStream(startedProcess.getInputStream(), outputLimit, capturedBytes));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                    () -> readStream(startedProcess.getErrorStream(), outputLimit, capturedBytes));

            boolean finished = process.waitFor(budget.timeout().toMillis(), TimeUnit.MILLISECONDS);
            Duration duration = Duration.between(start, Instant.now());

            if (running.cancelled.get()) {
                terminateTree(process);
                return result(-1, duration, ProcessStatus.CANCELLED,
                        getOutput(stdout), getOutput(stderr), processId);
            }
            if (!finished) {
                terminateTree(process);
                return result(-1, duration, ProcessStatus.TIMEOUT,
                        getOutput(stdout), getOutput(stderr),
                        "Execution timed out after " + budget.timeout(), processId);
            }

            return result(process.exitValue(), duration,
                    process.exitValue() == 0 ? ProcessStatus.SUCCESS : ProcessStatus.FAILED,
                    getOutput(stdout), getOutput(stderr), processId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) terminateTree(process);
            return result(-1, Duration.between(start, Instant.now()), ProcessStatus.CANCELLED,
                    "", "Process execution interrupted", processId);
        } catch (Exception e) {
            if (process != null) terminateTree(process);
            LOG.errorf(e, "Process execution error for command: %s", spec.command());
            return result(-1, Duration.between(start, Instant.now()), ProcessStatus.START_FAILED,
                    "", e.getMessage(), processId);
        } finally {
            if (processId != null) activeProcesses.remove(processId, running);
        }
    }

    @Override
    public Uni<Void> cancel(String processId) {
        return Uni.createFrom().item(() -> {
            RunningProcess running = activeProcesses.get(processId);
            if (running != null) {
                running.cancelled.set(true);
                terminateTree(running.process);
            }
            return null;
        });
    }

    private static void terminateTree(Process process) {
        // Destroy descendants before the parent so shells cannot leave children behind.
        process.descendants().forEach(handle -> {
            try { handle.destroyForcibly(); } catch (RuntimeException ignored) { }
        });
        if (process.isAlive()) process.destroyForcibly();
    }

    private static String readStream(InputStream input, long limit, AtomicLong totalCaptured) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            boolean truncated = false;
            while ((count = input.read(buffer)) != -1) {
                int accepted = reserveBytes(totalCaptured, limit, count);
                if (accepted > 0) output.write(buffer, 0, accepted);
                if (accepted < count) truncated = true;
            }
            String text = output.toString(StandardCharsets.UTF_8);
            return truncated ? text + "\n[OUTPUT TRUNCATED]" : text;
        } catch (Exception e) {
            return "Error reading process output: " + e.getMessage();
        }
    }

    private static int reserveBytes(AtomicLong captured, long limit, int requested) {
        if (limit == 0) return requested;
        while (true) {
            long current = captured.get();
            long available = Math.max(0L, limit - current);
            int accepted = (int) Math.min(requested, available);
            if (captured.compareAndSet(current, current + accepted)) return accepted;
        }
    }

    private static String getOutput(CompletableFuture<String> output) {
        try { return output.get(2, TimeUnit.SECONDS); }
        catch (Exception e) { return "[OUTPUT UNAVAILABLE: " + e.getMessage() + "]"; }
    }

    private static ProcessResult result(
            int exitCode, Duration duration, ProcessStatus status,
            String stdout, String stderr, String processId) {
        return new ProcessResult(exitCode, duration, status, stdout, stderr, processId);
    }

    private static ProcessResult result(
            int exitCode, Duration duration, ProcessStatus status,
            String stdout, String stderr, String message, String processId) {
        String diagnostic = message == null || message.isBlank()
                ? stderr
                : stderr == null || stderr.isBlank()
                        ? message
                        : stderr + "\n" + message;
        return new ProcessResult(exitCode, duration, status, stdout, diagnostic, processId);
    }

    private static final class RunningProcess {
        private final Process process;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private RunningProcess(Process process) {
            this.process = process;
        }
    }
}
