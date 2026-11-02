package tech.kayys.cifr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.exec.ExecutionResult;
import tech.kayys.cifr.engine.verify.Diagnostic;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link OutputProcessor}.
 */
@ApplicationScoped
public class DefaultOutputProcessor implements OutputProcessor {

    @Override
    public Uni<ProcessedOutput> process(String executionId, ExecutionResult result) {
        return Uni.createFrom().item(() -> {
            if (result == null) {
                return new ProcessedOutput(OutputSummary.failure("No execution result", 1), List.of(), List.of());
            }

            OutputSummary summary = result.exitCode() == 0
                ? OutputSummary.success("Command completed with exit code 0")
                : OutputSummary.failure("Command failed with exit code " + result.exitCode(), result.exitCode());

            List<Diagnostic> diagnostics = new ArrayList<>(result.diagnostics());
            return new ProcessedOutput(summary, diagnostics, List.of());
        });
    }
}
