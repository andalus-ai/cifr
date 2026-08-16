package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

public interface ToolAdapter {

    boolean supports(ProjectToolchain toolchain);

    Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain);
}
