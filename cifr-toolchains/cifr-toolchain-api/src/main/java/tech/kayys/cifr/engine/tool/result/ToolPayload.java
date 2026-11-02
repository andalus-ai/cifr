package tech.kayys.cifr.engine.tool.result;
public record ToolPayload(String content, String mimeType, boolean truncated, String cursor) {
    public static ToolPayload text(String content) { return new ToolPayload(content, "text/plain", false, null); }
    public static ToolPayload json(String content) { return new ToolPayload(content, "application/json", false, null); }
    public static ToolPayload empty() { return new ToolPayload("", "text/plain", false, null); }
}
