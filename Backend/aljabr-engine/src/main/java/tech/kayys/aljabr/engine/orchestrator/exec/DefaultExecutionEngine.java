package tech.kayys.aljabr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.exec.ExecutionResult;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.output.DefaultOutputProcessor;
import tech.kayys.aljabr.engine.orchestrator.output.OutputProcessor;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionBudget;
import tech.kayys.aljabr.engine.verify.Diagnostic;
import tech.kayys.aljabr.engine.verify.DiagnosticSeverity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DefaultExecutionEngine implements ExecutionEngine {

    private static final Logger LOG = Logger.getLogger(DefaultExecutionEngine.class);

    @Inject
    OutputProcessor outputProcessor;

    public DefaultExecutionEngine() {}

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
                ExecutionResult failed = new ExecutionResult(
                    -1, "", "No command provided",
                    List.of(new Diagnostic(DiagnosticSeverity.ERROR.name(), null, -1, -1, "No command provided")),
                    Optional.empty(), false
                );
                processor.process(executionId, failed).await().indefinitely();
                return failed;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(tool.command());
                if (tool.workingDirectory() != null) {
                    pb.directory(tool.workingDirectory().toFile());
                }
                if (tool.environment() != null) {
                    var env = pb.environment();
                    env.clear();
                    for (String key : tool.environment().allowedInheritedVariables()) {
                        String val = System.getenv(key);
                        if (val != null) env.put(key, val);
                    }
                    env.putAll(tool.environment().variables());
                }

                LOG.infof("Starting process [%s]: %s", executionId, String.join(" ", tool.command()));
                Process process = pb.start();

                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

                boolean finished = process.waitFor(currentBudget.timeout().toMillis(), TimeUnit.MILLISECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    ExecutionResult timedOut = new ExecutionResult(
                        -1, "", "Process timed out after " + currentBudget.timeout(),
                        List.of(new Diagnostic(DiagnosticSeverity.ERROR.name(), null, -1, -1, "Timeout")),
                        Optional.empty(), true
                    );
                    processor.process(executionId, timedOut).await().indefinitely();
                    return timedOut;
                }

                int exitCode = process.exitValue();
                String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
                String stderr = stderrFuture.get(2, TimeUnit.SECONDS);

                List<Diagnostic> diagnostics = exitCode != 0
                    ? List.of(new Diagnostic(DiagnosticSeverity.ERROR.name(), null, -1, -1, "Exit code " + exitCode))
                    : List.of();

                ExecutionResult result = new ExecutionResult(exitCode, stdout, stderr, diagnostics, Optional.empty(), false);
                processor.process(executionId, result).await().indefinitely();
                LOG.infof("Process [%s] finished with exitCode=%d", executionId, exitCode);
                return result;

            } catch (Exception e) {
                ExecutionResult crashed = new ExecutionResult(
                    -1, "", "Execution failed: " + e.getMessage(),
                    List.of(new Diagnostic(DiagnosticSeverity.ERROR.name(), null, -1, -1, e.getMessage())),
                    Optional.empty(), false
                );
                processor.process(executionId, crashed).await().indefinitely();
                return crashed;
            }
        });
    }

    private String readStream(java.io.InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Error reading stream: " + e.getMessage();
        }
    }
}
