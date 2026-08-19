package tech.kayys.aljabr.engine.orchestrator.policy;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;

import java.nio.file.Path;

public interface ToolPolicy {

    Uni<ToolPolicyDecision> evaluate(ToolIntent intent, ResolvedTool resolved, Path workspaceRoot);
}
