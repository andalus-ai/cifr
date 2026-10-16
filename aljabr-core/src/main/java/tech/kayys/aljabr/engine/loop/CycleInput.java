package tech.kayys.aljabr.engine.loop;

/**
 * Input state provided to an atomic decision cycle.
 */
public record CycleInput(
    String taskId,
    int cycleNumber,
    String objective
) {
    public static CycleInput of(String taskId, int cycleNumber, String objective) {
        return new CycleInput(taskId, cycleNumber, objective);
    }
}
