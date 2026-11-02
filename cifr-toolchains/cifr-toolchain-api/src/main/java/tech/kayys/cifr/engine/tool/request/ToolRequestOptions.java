package tech.kayys.cifr.engine.tool.request;
public record ToolRequestOptions(int priority, long timeoutMs, String cachePolicy) {
    public static ToolRequestOptions defaults() { return new ToolRequestOptions(5, 30_000L, "auto"); }
}
