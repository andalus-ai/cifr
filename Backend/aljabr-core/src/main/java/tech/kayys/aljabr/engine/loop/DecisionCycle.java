package tech.kayys.aljabr.engine.loop;

/**
 * Atomic reasoning and execution iteration record.
 */
public record DecisionCycle(
    CycleId id,
    String taskId,
    int sequence,
    CycleInput input,
    AgentDecision decision,
    CycleOutcome outcome
) {
    public static DecisionCycle of(String taskId, int sequence, CycleInput input, AgentDecision decision, CycleOutcome outcome) {
        return new DecisionCycle(CycleId.generate(), taskId, sequence, input, decision, outcome);
    }
}
