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
public class MakeToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain != null && (toolchain.buildSystem() == BuildSystem.MAKE || toolchain.buildSystem() == BuildSystem.MESON);
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();

            if (toolchain.buildSystem() == BuildSystem.MESON) {
                switch (intent.kind()) {
                    case BUILD -> command.addAll(List.of("meson", "compile", "-C", "build"));
                    case RUN_TEST, RUN_TESTS -> command.addAll(List.of("meson", "test", "-C", "build"));
                    default -> command.addAll(List.of("meson", "setup", "build"));
                }
                return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
            }

            // Standard Make
            command.add("make");
            switch (intent.kind()) {
                case BUILD -> {
                    // default 'make'
                }
                case RUN_TEST, RUN_TESTS -> command.add("test");
                case FORMAT -> {
                    command.clear();
                    command.addAll(List.of("clang-format", "-i"));
                    Object file = intent.arguments().get("file");
                    command.add(file != null ? file.toString() : "src/*.c");
                }
                case LINT -> {
                    command.clear();
                    command.addAll(List.of("clang-tidy"));
                    Object file = intent.arguments().get("file");
                    command.add(file != null ? file.toString() : "src/*.c");
                }
                default -> {
                    Object rawCmd = intent.arguments().get("command");
                    if (rawCmd != null) {
                        return new ResolvedTool(intent.kind(), List.of(rawCmd.toString().split("\\s+")), toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
                    }
                    command.add("all");
                }
            }

            return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
        });
    }
}
