package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GenericToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return true; // Fallback adapter supports everything
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();
            var root = toolchain != null ? toolchain.rootDirectory() : java.nio.file.Path.of(".");

            switch (intent.kind()) {
                case GIT_STATUS -> command.addAll(List.of("git", "status", "--porcelain"));
                case GIT_DIFF -> command.addAll(List.of("git", "diff"));
                case LIST_FILES -> command.addAll(List.of("ls", "-la"));
                case RUN_COMMAND -> {
                    Object cmd = intent.arguments().get("command");
                    if (cmd != null) {
                        command.addAll(List.of(cmd.toString().split("\\s+")));
                    } else {
                        command.addAll(List.of("echo", "no command specified"));
                    }
                }
                default -> command.addAll(List.of("echo", "unsupported intent: " + intent.kind()));
            }

            return new ResolvedTool(intent.kind(), command, root, ExecutionEnvironment.defaultSandboxed());
        });
    }
}
