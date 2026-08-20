package tech.kayys.aljabr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.exec.ExecutionResult;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionBudget;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Default implementation of {@link ExecutionEngine}.
 * Runs resolved tool commands as OS processes, applying budget constraints.
 */
@ApplicationScoped
public class DefaultExecutionEngine implements ExecutionEngine {

    private static final Logger log = Logger.getLogger(DefaultExecutionEngine.class.getName());

    public DefaultExecutionEngine() {}

    @Override
    public Uni<ExecutionResult> execute(String executionId, ResolvedTool tool, ExecutionBudget budget) {
        log.info("Executing [" + executionId + "]: " + tool.command() + " in " + tool.workingDirectory());

        return Uni.createFrom().emitter(emitter -> {
            try {
                List<String> command = tool.command();
                if (command == null || command.isEmpty()) {
                    emitter.complete(new ExecutionResult(1, "No command specified", "", List.of(), Optional.empty(), false));
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                if (tool.workingDirectory() != null) {
                    pb.directory(tool.workingDirectory().toFile());
                }

                // Apply environment variables from the resolved tool's environment
                ExecutionEnvironment env = tool.environment();
                if (env != null && env.variables() != null) {
                    pb.environment().putAll(env.variables());
                }

                pb.redirectErrorStream(true);

                Process process = pb.start();
                Duration timeout = budget != null ? budget.timeout() : Duration.ofMinutes(5);
                byte[] output = process.getInputStream().readAllBytes();
                boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    emitter.complete(new ExecutionResult(124,
                        "Timed out after " + timeout.getSeconds() + "s", "", List.of(), Optional.empty(), true));
                    return;
                }

                int exitCode = process.exitValue();
                String stdout = new String(output);
                if (budget != null && output.length > budget.maxOutputBytes()) {
                    stdout = stdout.substring(0, (int) Math.min(stdout.length(), budget.maxOutputBytes()))
                        + "\n[OUTPUT TRUNCATED]";
                }

                emitter.complete(new ExecutionResult(exitCode, stdout, "", List.of(), Optional.empty(), false));

            } catch (Exception e) {
                log.warning("Execution error [" + executionId + "]: " + e.getMessage());
                emitter.complete(new ExecutionResult(-1, e.getMessage(), "", List.of(), Optional.empty(), false));
            }
        });
    }
}
