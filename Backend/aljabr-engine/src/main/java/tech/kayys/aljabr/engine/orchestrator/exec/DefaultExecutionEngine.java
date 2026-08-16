package tech.kayys.aljabr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.output.DefaultOutputProcessor;
import tech.kayys.aljabr.engine.orchestrator.output.ExecutionStatus;
import tech.kayys.aljabr.engine.orchestrator.output.OutputProcessor;
import tech.kayys.aljabr.engine.orchestrator.output.ProcessedOutput;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionBudget;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DefaultExecutionEngine implements ExecutionEngine {

    private static final Logger LOG = Logger.getLogger(DefaultExecutionEngine.class);

    @Inject
    OutputProcessor outputProcessor;

    public DefaultExecutionEngine() {
    }

    public DefaultExecutionEngine(OutputProcessor outputProcessor) {
        this.outputProcessor = outputProcessor;
    }

    @Override
    public Uni<ExecutionResult> execute(String executionId, ResolvedTool tool, ExecutionBudget budget) {
        return Uni.createFrom().item(() -> {
            long startTime = System.currentTimeMillis();
            ExecutionBudget currentBudget = budget != null ? budget : ExecutionBudget.defaultBudget();
            OutputProcessor processor = outputProcessor != null ? outputProcessor : new DefaultOutputProcessor();

            if (tool == null || tool.command().isEmpty()) {
                ProcessedOutput out = processor.process(1, "", "No command provided", currentBudget.maxOutputBytes()).await().indefinitely();
                return new ExecutionResult(executionId, ExecutionStatus.FAILED, 1, Duration.ZERO, out);
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(tool.command());
                if (tool.workingDirectory() != null) {
                    pb.directory(tool.workingDirectory().toFile());
                }

                // Sandbox environment inheritance
                Map<String, String> env = pb.environment();
                if (tool.environment() != null) {
                    env.clear();
                    for (String allowedKey : tool.environment().allowedInheritedVariables()) {
                        String val = System.getenv(allowedKey);
                        if (val != null) {
                            env.put(allowedKey, val);
                        }
                    }
                    env.putAll(tool.environment().variables());
                }

                LOG.infof("Starting process [%s]: %s in %s", executionId, String.join(" ", tool.command()), tool.workingDirectory());
                Process process = pb.start();

                // Read output asynchronously
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

                boolean finished = process.waitFor(currentBudget.timeout().toMillis(), TimeUnit.MILLISECONDS);
                long durationMs = System.currentTimeMillis() - startTime;

                if (!finished) {
                    LOG.warnf("Process [%s] timed out after %d ms. Forcibly terminating.", executionId, currentBudget.timeout().toMillis());
                    process.destroyForcibly();
                    ProcessedOutput out = processor.process(-1, "", "Process timed out after " + currentBudget.timeout(), currentBudget.maxOutputBytes()).await().indefinitely();
                    return new ExecutionResult(executionId, ExecutionStatus.TIMEOUT, -1, Duration.ofMillis(durationMs), out);
                }

                int exitCode = process.exitValue();
                String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
                String stderr = stderrFuture.get(2, TimeUnit.SECONDS);

                ProcessedOutput processed = processor.process(exitCode, stdout, stderr, currentBudget.maxOutputBytes()).await().indefinitely();
                ExecutionStatus status = exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;

                LOG.infof("Process [%s] finished with exitCode=%d in %d ms", executionId, exitCode, durationMs);
                return new ExecutionResult(executionId, status, exitCode, Duration.ofMillis(durationMs), processed);

            } catch (Exception e) {
                LOG.errorf("Error executing process [%s]: %s", executionId, e.getMessage());
                ProcessedOutput out = processor.process(1, "", "Execution failed: " + e.getMessage(), currentBudget.maxOutputBytes()).await().indefinitely();
                return new ExecutionResult(executionId, ExecutionStatus.CRASHED, 1, Duration.ofMillis(System.currentTimeMillis() - startTime), out);
            }
        });
    }

    private String readStream(java.io.InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error reading stream: " + e.getMessage();
        }
    }
}
