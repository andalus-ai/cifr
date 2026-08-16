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
public class CMakeToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain != null && toolchain.buildSystem() == BuildSystem.CMAKE;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();

            switch (intent.kind()) {
                case BUILD -> {
                    command.add("cmake");
                    command.add("--build");
                    command.add("build");
                }
                case RUN_TEST -> {
                    command.add("ctest");
                    command.add("--test-dir");
                    command.add("build");
                    command.add("--output-on-failure");
                    Object testTarget = intent.arguments().get("test");
                    if (testTarget != null && !testTarget.toString().isBlank()) {
                        command.add("-R");
                        command.add(testTarget.toString());
                    }
                }
                case RUN_TESTS -> {
                    command.add("ctest");
                    command.add("--test-dir");
                    command.add("build");
                    command.add("--output-on-failure");
                }
                case FORMAT -> {
                    command.add("clang-format");
                    command.add("-i");
                    Object file = intent.arguments().get("file");
                    command.add(file != null ? file.toString() : "src/*.cpp");
                }
                case LINT -> {
                    command.add("clang-tidy");
                    Object file = intent.arguments().get("file");
                    if (file != null) {
                        command.add(file.toString());
                    } else {
                        command.add("-p");
                        command.add("build");
                    }
                }
                default -> {
                    Object rawCmd = intent.arguments().get("command");
                    if (rawCmd != null) {
                        return new ResolvedTool(intent.kind(), List.of(rawCmd.toString().split("\\s+")), toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
                    }
                    command.add("cmake");
                    command.add("-B");
                    command.add("build");
                    command.add("-S");
                    command.add(".");
                }
            }

            return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
        });
    }
}
