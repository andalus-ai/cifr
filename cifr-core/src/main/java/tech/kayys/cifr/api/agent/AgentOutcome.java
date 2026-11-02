package tech.kayys.cifr.api.agent;

public record AgentOutcome(
    boolean success,
    String finalMessage,
    String error
) {}
