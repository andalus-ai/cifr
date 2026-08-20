package tech.kayys.aljabr.api.agent;

public record AljabrResult(
    boolean success,
    String summary,
    AljabrState finalState
) {}
