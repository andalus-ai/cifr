package tech.kayys.cifr.engine.agent;

import java.util.Map;

/**
 * Structured model invocation request compiled by the PromptCompiler.
 */
public record ModelRequest(
    String systemPrompt,
    String userPrompt,
    Map<String, Object> parameters
) {
    public ModelRequest {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    public static ModelRequest of(String systemPrompt, String userPrompt) {
        return new ModelRequest(systemPrompt, userPrompt, Map.of());
    }
}
