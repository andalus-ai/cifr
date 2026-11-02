package tech.kayys.cifr.engine.retrieval;

/**
 * Resource and execution budget for a retrieval operation.
 */
public record RetrievalBudget(
    int maxCandidates,
    int maxSources,
    int maxBytes,
    long maxLatencyMs
) {
    public static RetrievalBudget defaults() {
        return new RetrievalBudget(30, 10, 512 * 1024, 2000L);
    }

    public static RetrievalBudget local() {
        return new RetrievalBudget(15, 5, 256 * 1024, 1000L);
    }
}
