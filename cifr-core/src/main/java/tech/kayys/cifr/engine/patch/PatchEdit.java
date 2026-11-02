package tech.kayys.cifr.engine.patch;

public record PatchEdit(
    String expectedText,
    String replacementText
) {}
