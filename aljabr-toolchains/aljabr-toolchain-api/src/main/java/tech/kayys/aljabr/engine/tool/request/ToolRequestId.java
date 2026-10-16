package tech.kayys.aljabr.engine.tool.request;
public record ToolRequestId(String value) {
    public static ToolRequestId generate() { return new ToolRequestId("req-" + java.util.UUID.randomUUID()); }
}
