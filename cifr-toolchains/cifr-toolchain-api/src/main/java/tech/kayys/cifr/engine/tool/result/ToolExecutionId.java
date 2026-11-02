package tech.kayys.cifr.engine.tool.result;
public record ToolExecutionId(String value) {
    public static ToolExecutionId generate() { return new ToolExecutionId("exec-" + java.util.UUID.randomUUID()); }
}
