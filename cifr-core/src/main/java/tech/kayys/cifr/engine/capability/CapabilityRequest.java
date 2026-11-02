package tech.kayys.cifr.engine.capability;

import java.util.Map;

/**
 * Invocation request for an authorized capability.
 */
public record CapabilityRequest(
    String requestId,
    String capabilityId,
    RequestOrigin origin,
    String principalId,
    String justification,
    Map<String, Object> parameters
) {
    public CapabilityRequest {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    public static CapabilityRequest fromModel(String capabilityId, String justification, Map<String, Object> parameters) {
        return new CapabilityRequest(
            "req-" + java.util.UUID.randomUUID(),
            capabilityId,
            RequestOrigin.MODEL,
            "agent",
            justification,
            parameters
        );
    }
}
