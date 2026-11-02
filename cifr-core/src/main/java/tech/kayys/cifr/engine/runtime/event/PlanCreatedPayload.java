package tech.kayys.cifr.engine.runtime.event;

import java.util.List;

/**
 * Payload recorded when a task execution plan or task graph is generated.
 */
public record PlanCreatedPayload(
    String planId,
    int stepCount,
    List<String> stepObjectives
) implements EventPayload {
    public PlanCreatedPayload {
        stepObjectives = stepObjectives != null ? List.copyOf(stepObjectives) : List.of();
    }

    @Override
    public String payloadType() {
        return "PlanCreated";
    }
}
