package tech.kayys.aljabr.engine.tool.policy;
public record ToolOutputPlan(int maxResults, boolean includeRaw, boolean summaryOnly, long maxTokens) {
    public static ToolOutputPlan local() { return new ToolOutputPlan(10, false, false, 1500L); }
    public static ToolOutputPlan cloud() { return new ToolOutputPlan(30, true, false, 8000L); }
}
