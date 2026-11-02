package tech.kayys.cifr.engine.tool.fs;

public record SearchMatch(
    String path,
    int line,
    String snippet
) {}
