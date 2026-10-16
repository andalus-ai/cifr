package tech.kayys.aljabr.engine.tool.protocol;
public record ToolDescriptor(
    CapabilityId id,
    String name,
    String description,
    ToolInputSchema inputSchema,
    ToolOutputSchema outputSchema,
    ToolMetadata metadata
) {
    public static ToolDescriptor readOnly(CapabilityId id, String name, String description) {
        return new ToolDescriptor(id, name, description, ToolInputSchema.empty(), ToolOutputSchema.empty(), ToolMetadata.readOnly());
    }
}
