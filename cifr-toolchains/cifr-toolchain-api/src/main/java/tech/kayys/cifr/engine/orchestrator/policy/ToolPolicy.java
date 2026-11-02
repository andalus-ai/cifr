package tech.kayys.cifr.engine.orchestrator.policy;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.orchestrator.ResolvedTool;
import tech.kayys.cifr.engine.orchestrator.ToolIntent;

import java.nio.file.Path;

public interface ToolPolicy {

    Uni<ToolPolicyDecision> evaluate(ToolIntent intent, ResolvedTool resolved, Path workspaceRoot);
}
