package tech.kayys.aljabr.engine.patch;

public record PatchHunk(
    int startLine,
    int endLine,
    String expectedOldText,
    String replacementText
) {}
