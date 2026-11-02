package tech.kayys.cifr.engine.tool.protocol;
public record ToolMetadata(
    ToolCost cost,
    ToolAuthority requiredAuthority,
    boolean deterministic,
    boolean cacheable,
    boolean idempotent,
    boolean mutatesWorkspace,
    boolean requiresNetwork
) {
    public static ToolMetadata readOnly() {
        return new ToolMetadata(ToolCost.LOW, ToolAuthority.READ, true, true, true, false, false);
    }
    public static ToolMetadata execute() {
        return new ToolMetadata(ToolCost.MEDIUM, ToolAuthority.EXECUTE, false, false, false, false, false);
    }
    public static ToolMetadata mutating() {
        return new ToolMetadata(ToolCost.MEDIUM, ToolAuthority.MUTATE, false, false, false, true, false);
    }
}
