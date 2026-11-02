package tech.kayys.cifr.engine.context;

public record ContextSection(
    String header,
    String content,
    int estimatedTokens,
    int priority
) {
    public static ContextSection of(String header, String content, int priority) {
        int tokens = content != null ? Math.max(1, content.length() / 4) : 0;
        return new ContextSection(header, content != null ? content : "", tokens, priority);
    }
}
