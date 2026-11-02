package tech.kayys.cifr.api.agent;

import tech.kayys.cifr.engine.loop.CodingPlan;

public record CifrState(
    String executionId,
    AgentPhase phase,
    CodingPlan plan,
    CodingProgress progress,
    WorkspaceRevision workspaceRevision,
    AgentOutcome outcome
) {}
