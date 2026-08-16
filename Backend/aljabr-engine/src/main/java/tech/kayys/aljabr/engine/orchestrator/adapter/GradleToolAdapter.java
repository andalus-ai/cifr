package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GradleToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain != null && toolchain.buildSystem() == BuildSystem.GRADLE;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();
            boolean hasWrapper = toolchain != null && Files.exists(toolchain.rootDirectory().resolve("gradlew"));
            command.add(hasWrapper ? "./gradlew" : "gradle");

            switch (intent.kind()) {
                case BUILD -> command.add("classes");
                case RUN_TEST -> {
                    command.add("test");
                    Object testTarget = intent.arguments().get("test");
                    if (testTarget != null && !testTarget.toString().isBlank()) {
                        command.add("--tests");
                        command.add(testTarget.toString());
                    }
                }
                case RUN_TESTS -> command.add("test");
                case FORMAT -> command.add("spotlessApply");
                case LINT -> command.add("check");
                default -> {
                    Object rawCmd = intent.arguments().get("command");
                    if (rawCmd != null) {
                        return new ResolvedTool(intent.kind(), List.of(rawCmd.toString().split("\\s+")), toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
                    }
                    command.add("tasks");
                }
            }

            return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
        });
    }
}
