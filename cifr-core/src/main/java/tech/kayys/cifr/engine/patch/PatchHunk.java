package tech.kayys.cifr.engine.patch;

public record PatchHunk(
    int startLine,
    int endLine,
    String expectedOldText,
    String replacementText
) {}
