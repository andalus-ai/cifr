package tech.kayys.aljabr.engine.exec.intelligence;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionBudget;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DefaultProcessRunner implements ProcessRunner {

    private static final Logger LOG = Logger.getLogger(DefaultProcessRunner.class);
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public Uni<ProcessResult> execute(ProcessSpec spec) {
        return Uni.createFrom().item(() -> {
            if (spec == null || spec.command().isEmpty()) {
                return new ProcessResult(-1, Duration.ZERO, ProcessStatus.START_FAILED, "", "No command specified");
            }

            Instant start = Instant.now();
            ExecutionBudget budget = spec.budget() != null ? spec.budget() : ExecutionBudget.defaultBudget();

            try {
                ProcessBuilder pb = new ProcessBuilder(spec.command());
                if (spec.workingDirectory() != null) {
                    pb.directory(spec.workingDirectory().toFile());
                }
                if (!spec.environment().isEmpty()) {
                    pb.environment().putAll(spec.environment());
                }

                Process process = pb.start();
                String pid = String.valueOf(process.pid());
                activeProcesses.put(pid, process);

                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream(), budget.maxOutputBytes()));
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream(), budget.maxOutputBytes()));

                boolean finished = process.waitFor(budget.timeout().toMillis(), TimeUnit.MILLISECONDS);
                activeProcesses.remove(pid);

                Duration duration = Duration.between(start, Instant.now());

                if (!finished) {
                    process.destroyForcibly();
                    return new ProcessResult(-1, duration, ProcessStatus.TIMEOUT, "", "Execution timed out after " + budget.timeout());
                }

                int exitCode = process.exitValue();
                String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
                String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
                ProcessStatus status = exitCode == 0 ? ProcessStatus.SUCCESS : ProcessStatus.FAILED;

                return new ProcessResult(exitCode, duration, status, stdout, stderr);

            } catch (Exception e) {
                LOG.errorf(e, "Process execution error for command: %s", spec.command());
                Duration duration = Duration.between(start, Instant.now());
                return new ProcessResult(-1, duration, ProcessStatus.START_FAILED, "", e.getMessage());
            }
        });
    }

    @Override
    public Uni<Void> cancel(String processId) {
        return Uni.createFrom().item(() -> {
            Process process = activeProcesses.remove(processId);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            return null;
        });
    }

    private String readStream(java.io.InputStream is, long maxBytes) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            long bytesRead = 0;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                bytesRead += line.length() + 1;
                if (maxBytes > 0 && bytesRead > maxBytes) {
                    sb.append("\n[OUTPUT TRUNCATED]");
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error reading stream: " + e.getMessage();
        }
    }
}
