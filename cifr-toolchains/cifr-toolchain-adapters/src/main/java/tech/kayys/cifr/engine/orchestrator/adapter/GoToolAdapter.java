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
public class GoToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain.buildSystem() == BuildSystem.GO_MODULE || toolchain.buildSystem() == BuildSystem.GO;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        List<String> cmd = new ArrayList<>();
        cmd.add("go");

        if (intent.kind() == ToolKind.RUN_TEST || intent.kind() == ToolKind.RUN_TESTS) {
            cmd.add("test");
            cmd.add("./...");
        } else if (intent.kind() == ToolKind.FORMAT) {
            cmd.add("fmt");
            cmd.add("./...");
        } else if (intent.kind() == ToolKind.LINT || intent.kind() == ToolKind.STATIC_ANALYSIS) {
            cmd.add("vet");
            cmd.add("./...");
        } else {
            cmd.add("build");
            cmd.add("./...");
        }

        return Uni.createFrom().item(new ResolvedTool(
            intent.kind(),
            cmd,
            toolchain.rootDirectory(),
            ExecutionEnvironment.defaultSandboxed()
        ));
    }
}
