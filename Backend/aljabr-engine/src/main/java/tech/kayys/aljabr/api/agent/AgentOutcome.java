package tech.kayys.aljabr.api.agent;

public record AgentOutcome(
    boolean success,
    String finalMessage,
    String error
) {}
