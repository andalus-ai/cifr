package tech.kayys.aljabr.engine.index;

/**
 * Importance score for a file relative to the current task.
 * Enables Wayang to pre-rank files before presenting them to the model.
 */
public record FileImportance(
    String fileId,
    double score,
    ImportanceReason primaryReason
) {
    public static FileImportance high(String fileId, ImportanceReason reason) {
        return new FileImportance(fileId, 1.0, reason);
    }

    public static FileImportance of(String fileId, double score, ImportanceReason reason) {
        return new FileImportance(fileId, score, reason);
    }
}
