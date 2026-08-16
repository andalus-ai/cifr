package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.ToolKind;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SonarToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        if (toolchain == null) return false;
        return Files.exists(toolchain.rootDirectory().resolve("sonar-project.properties"));
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();
            command.add("sonar-scanner");

            var root = toolchain != null ? toolchain.rootDirectory() : java.nio.file.Path.of(".");
            String projectKey = root.getFileName() != null ? root.getFileName().toString() : "project";

            command.add("-Dsonar.projectKey=" + projectKey);
            command.add("-Dsonar.sources=.");
            command.add("-Dsonar.sarif.output=sonar-report.sarif");

            Object file = intent.arguments().get("file");
            if (file != null && !file.toString().isBlank()) {
                command.add("-Dsonar.inclusions=" + file);
            }

            return new ResolvedTool(intent.kind(), command, root, ExecutionEnvironment.defaultSandboxed());
        });
    }
}
