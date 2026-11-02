package tech.kayys.cifr.engine.tool.template;

import tech.kayys.cifr.engine.tool.ToolResult;

public record TemplateResult(boolean success, String message, String content) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
