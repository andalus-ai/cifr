package tech.kayys.aljabr.engine.task.runtime;

public sealed interface AgentSignal permits
    AgentSignal.PhaseDone,
    AgentSignal.TestsFailed,
    AgentSignal.PatchApplied,
    AgentSignal.RequirementsVerified,
    AgentSignal.ReviewAccepted,
    AgentSignal.StepFailed,
    AgentSignal.UserBlocked {

    record PhaseDone(String phase) implements AgentSignal {}
    record TestsFailed(int failureCount) implements AgentSignal {}
    record PatchApplied(String patchId) implements AgentSignal {}
    record RequirementsVerified(int count) implements AgentSignal {}
    record ReviewAccepted() implements AgentSignal {}
    record StepFailed(String reason) implements AgentSignal {}
    record UserBlocked(String reason) implements AgentSignal {}
}
