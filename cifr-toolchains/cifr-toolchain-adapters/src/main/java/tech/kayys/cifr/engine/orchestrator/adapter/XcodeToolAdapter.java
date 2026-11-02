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
public class XcodeToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain.buildSystem() == BuildSystem.XCODE;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        List<String> cmd = new ArrayList<>();

        if (intent.kind() == ToolKind.FORMAT) {
            cmd.add("clang-format");
            cmd.add("-i");
            if (intent.arguments().containsKey("file")) {
                cmd.add((String) intent.arguments().get("file"));
            }
        } else if (intent.kind() == ToolKind.RUN_TEST || intent.kind() == ToolKind.RUN_TESTS) {
            cmd.add("xcodebuild");
            cmd.add("test");
        } else {
            cmd.add("xcodebuild");
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
