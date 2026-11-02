package tech.kayys.cifr.engine.memory;

/**
 * Importance tier of a memory item for retrieval weighting and retention pruning.
 */
public enum MemoryImportance {
    LOW(0.25),
    NORMAL(0.50),
    HIGH(0.75),
    CRITICAL(1.00);

    private final double weight;

    MemoryImportance(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
