package tech.kayys.cifr.api.agent;

public record CifrResult(
    boolean success,
    String summary,
    CifrState finalState
) {}
