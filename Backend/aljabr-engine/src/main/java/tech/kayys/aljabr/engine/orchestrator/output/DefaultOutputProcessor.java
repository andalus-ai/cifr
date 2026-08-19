package tech.kayys.aljabr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.exec.ExecutionResult;
import tech.kayys.aljabr.engine.process.OutputArtifact;
import tech.kayys.aljabr.engine.verify.Diagnostic;
import tech.kayys.aljabr.engine.verify.DiagnosticSeverity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DefaultOutputProcessor implements OutputProcessor {

    private static final Path STORAGE_DIR = Paths.get(".aljabr/executions");
    private static final int MAX_SNIPPET_LINES = 50;

    @Override
    public Uni<ProcessedOutput> process(String executionId, ExecutionResult result) {
        return Uni.createFrom().item(() -> {
            String rawOutput = result.stdoutSummary() != null && !result.stdoutSummary().isBlank()
                ? result.stdoutSummary()
                : (result.stderrSummary() != null ? result.stderrSummary() : "");

            Path execDir = STORAGE_DIR.resolve(executionId != null ? executionId : "default");
            Path logPath = execDir.resolve("output.log");

            try {
                Files.createDirectories(execDir);
                Files.writeString(logPath, rawOutput, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}

            String[] lines = rawOutput.split("\n");
            int lineCount = lines.length;

            StringBuilder snippetBuilder = new StringBuilder();
            int startIdx = Math.max(0, lineCount - MAX_SNIPPET_LINES);
            for (int i = startIdx; i < lineCount; i++) {
                snippetBuilder.append(lines[i]).append("\n");
            }

            List<Diagnostic> diagnostics = new ArrayList<>(result.diagnostics());
            if (result.exitCode() != 0 && diagnostics.isEmpty()) {
                diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR.name(),
                    null,
                    -1,
                    -1,
                    "Process exited with code " + result.exitCode()
                ));
            }

            OutputSummary summary = new OutputSummary(
                result.exitCode(),
                Duration.ZERO,
                lineCount,
                snippetBuilder.toString().stripTrailing(),
                logPath
            );

            List<OutputArtifact> artifacts = new ArrayList<>();
            result.fullOutput().ifPresent(artifacts::add);
            artifacts.add(new OutputArtifact(logPath.toUri(), (long) rawOutput.length(), lineCount, false));

            return new ProcessedOutput(summary, diagnostics, artifacts);
        });
    }
}
