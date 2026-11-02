package tech.kayys.cifr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.orchestrator.ResolvedTool;
import tech.kayys.cifr.engine.orchestrator.ToolIntent;
import tech.kayys.cifr.engine.orchestrator.ToolKind;
import tech.kayys.cifr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.cifr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CMakeToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain.buildSystem() == BuildSystem.CMAKE;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        List<String> cmd = new ArrayList<>();

        if (intent.kind() == ToolKind.RUN_TEST || intent.kind() == ToolKind.RUN_TESTS) {
            cmd.add("ctest");
            cmd.add("--test-dir");
            cmd.add("build");
            cmd.add("--output-on-failure");
            if (intent.arguments().containsKey("test")) {
                cmd.add("-R");
                cmd.add((String) intent.arguments().get("test"));
            }
        } else if (intent.kind() == ToolKind.FORMAT) {
            cmd.add("clang-format");
            cmd.add("-i");
            if (intent.arguments().containsKey("file")) {
                cmd.add((String) intent.arguments().get("file"));
            }
        } else if (intent.kind() == ToolKind.LINT || intent.kind() == ToolKind.STATIC_ANALYSIS) {
            cmd.add("clang-tidy");
            if (intent.arguments().containsKey("file")) {
                cmd.add((String) intent.arguments().get("file"));
            }
        } else {
            cmd.add("cmake");
            cmd.add("--build");
            cmd.add("build");
        }

        return Uni.createFrom().item(new ResolvedTool(
            intent.kind(),
            cmd,
            toolchain.rootDirectory(),
            ExecutionEnvironment.defaultSandboxed()
        ));
    }
}
