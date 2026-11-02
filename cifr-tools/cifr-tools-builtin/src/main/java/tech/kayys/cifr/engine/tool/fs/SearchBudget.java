package tech.kayys.cifr.engine.tool.fs;

public record SearchBudget(
    int maxMatches,
    int maxFiles,
    int maxBytes
) {}
