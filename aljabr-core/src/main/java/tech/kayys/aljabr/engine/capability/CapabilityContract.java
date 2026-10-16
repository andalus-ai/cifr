package tech.kayys.aljabr.engine.capability;

import java.util.List;
import java.util.Map;

/**
 * Formal contract specifying input/output schemas, pre/postconditions, idempotency, and risk.
 */
public record CapabilityContract(
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    List<Precondition> preconditions,
    List<Postcondition> postconditions,
    Idempotency idempotency,
    CapabilityRisk risk
) {
    public CapabilityContract {
        inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
        outputSchema = outputSchema != null ? Map.copyOf(outputSchema) : Map.of();
        preconditions = preconditions != null ? List.copyOf(preconditions) : List.of();
        postconditions = postconditions != null ? List.copyOf(postconditions) : List.of();
    }

    public static CapabilityContract simple(CapabilityRisk risk, Idempotency idempotency) {
        return new CapabilityContract(Map.of(), Map.of(), List.of(), List.of(), idempotency, risk);
    }
}
