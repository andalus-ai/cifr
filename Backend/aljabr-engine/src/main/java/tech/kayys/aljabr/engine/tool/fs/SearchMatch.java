package tech.kayys.aljabr.engine.tool.fs;

public record SearchMatch(
    String path,
    int line,
    String snippet
) {}
