package tech.kayys.aljabr.engine.tool.fs;

public record SearchBudget(
    int maxMatches,
    int maxFiles,
    int maxBytes
) {}
