package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class MavenToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain != null && toolchain.buildSystem() == BuildSystem.MAVEN;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();
            command.add("mvn");

            switch (intent.kind()) {
                case BUILD -> {
                    command.add("compile");
                    command.add("-DskipTests");
                }
                case RUN_TEST -> {
                    command.add("test");
                    Object testTarget = intent.arguments().get("test");
                    if (testTarget != null && !testTarget.toString().isBlank()) {
                        command.add("-Dtest=" + testTarget);
                    }
                }
                case RUN_TESTS -> command.add("test");
                case FORMAT -> command.add("spotless:apply");
                case LINT -> command.add("checkstyle:check");
                default -> {
                    Object rawCmd = intent.arguments().get("command");
                    if (rawCmd != null) {
                        return new ResolvedTool(intent.kind(), List.of(rawCmd.toString().split("\\s+")), toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
                    }
                    command.add("validate");
                }
            }

            return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
        });
    }
}
