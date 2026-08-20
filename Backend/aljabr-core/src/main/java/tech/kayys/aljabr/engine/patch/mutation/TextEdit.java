package tech.kayys.aljabr.engine.patch.mutation;

public record TextEdit(
    int startLine,
    int endLine,
    String expectedText,
    String replacementText
) {
    public TextEdit(String expectedText, String replacementText) {
        this(1, 1, expectedText, replacementText);
    }
}
