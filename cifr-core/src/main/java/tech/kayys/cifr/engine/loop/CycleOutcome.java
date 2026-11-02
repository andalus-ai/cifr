package tech.kayys.cifr.engine.loop;

/**
 * Outcome resulting from processing an atomic decision cycle.
 */
public record CycleOutcome(
    boolean isTerminal,
    String summary
) {
    public static CycleOutcome continueLoop(String summary) {
        return new CycleOutcome(false, summary);
    }

    public static CycleOutcome terminal(String summary) {
        return new CycleOutcome(true, summary);
    }
}
