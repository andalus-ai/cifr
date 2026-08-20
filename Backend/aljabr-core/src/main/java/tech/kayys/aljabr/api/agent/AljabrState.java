package tech.kayys.aljabr.api.agent;

import tech.kayys.aljabr.engine.loop.CodingPlan;

public record AljabrState(
    String executionId,
    AgentPhase phase,
    CodingPlan plan,
    CodingProgress progress,
    WorkspaceRevision workspaceRevision,
    AgentOutcome outcome
) {}
