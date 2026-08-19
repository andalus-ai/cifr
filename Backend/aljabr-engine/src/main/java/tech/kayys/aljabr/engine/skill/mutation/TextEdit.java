package tech.kayys.aljabr.engine.skill.mutation;

public record TextEdit(
    int startLine,
    int endLine,
    String expectedText,
    String replacementText
) {}
