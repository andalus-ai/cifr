package tech.kayys.aljabr.engine.verify;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.loop.CodingTask;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class CommandVerificationEngine implements VerificationEngine {
    private static final Logger log = Logger.getLogger(CommandVerificationEngine.class.getName());

    @Override
    public Uni<VerificationReport> verify(CodingTask task, ChangeSet changes, AgentToolExecutor executor, List<Tool> tools) {
        log.info("Running verification for task: " + task.id());

        return Uni.createFrom().item(() -> {
            try {
                // Try executing a Maven compile to verify syntax
                // Later, this can fall back to Wayang tools if available
                String[] command = {"mvn", "compile", "-DskipTests"};
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(task.workspace().toFile());
                pb.redirectErrorStream(true); // merge stderr into stdout
                
                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    return new VerificationReport(true, List.of(new CheckResult("mvn compile", true, "Success")), List.of());
                } else {
                    Diagnostic diagnostic = new Diagnostic("ERROR", null, 0, 0, output.toString());
                    return new VerificationReport(false, List.of(new CheckResult("mvn compile", false, "Failed with exit code " + exitCode)), List.of(diagnostic));
                }
            } catch (Exception e) {
                log.warning("Verification failed to run: " + e.getMessage());
                return new VerificationReport(false, List.of(new CheckResult("Verification Error", false, e.getMessage())), List.of());
            }
        });
    }
}
