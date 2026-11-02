package tech.kayys.cifr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.orchestrator.ResolvedTool;
import tech.kayys.cifr.engine.orchestrator.ToolIntent;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

public interface ToolAdapter {

    boolean supports(ProjectToolchain toolchain);

    Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain);
}
