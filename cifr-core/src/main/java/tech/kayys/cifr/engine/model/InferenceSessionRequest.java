package tech.kayys.cifr.engine.model;

import java.util.Map;

/**
 * Structured request for model inference with attached requirements and budget.
 */
public record InferenceSessionRequest(
    String requestId,
    String prompt,
    ModelRequirements requirements,
    InferenceBudget budget,
    Map<String, Object> parameters
) {
    public InferenceSessionRequest {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    public static InferenceSessionRequest of(String prompt, ModelRequirements requirements) {
        return new InferenceSessionRequest(
            "req-" + java.util.UUID.randomUUID(),
            prompt,
            requirements != null ? requirements : ModelRequirements.standardLocal(),
            InferenceBudget.localDefault(),
            Map.of()
        );
    }
}
