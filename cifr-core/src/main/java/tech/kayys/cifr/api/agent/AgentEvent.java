package tech.kayys.cifr.api.agent;

public sealed interface AgentEvent {

    record ObservationCompleted() implements AgentEvent {}

    record PlanCreated() implements AgentEvent {}

    record TaskSucceeded() implements AgentEvent {}

    record TaskFailed() implements AgentEvent {}

    record VerificationCompleted() implements AgentEvent {}

    record ReviewCompleted() implements AgentEvent {}

    record EvaluationCompleted() implements AgentEvent {}

    record UserApproved() implements AgentEvent {}

    record UserDenied() implements AgentEvent {}

    record BudgetExceeded() implements AgentEvent {}

    record CancellationRequested() implements AgentEvent {}

    // Telemetry / Observable events
    record PhaseStarted(String executionId, AgentPhase phase, java.time.Instant startedAt) implements AgentEvent {}
    
    record PhaseCompleted(String executionId, AgentPhase phase, java.time.Instant completedAt) implements AgentEvent {}
    
    record PhaseFailed(String executionId, AgentPhase phase, java.time.Instant failedAt, String reason) implements AgentEvent {}
}
