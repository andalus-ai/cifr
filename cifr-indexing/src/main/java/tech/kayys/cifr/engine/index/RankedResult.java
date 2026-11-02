package tech.kayys.cifr.engine.index;

import java.util.List;

/**
 * A ranked wrapper around any repository query result.
 * Provides a deterministic score and the reasons driving it.
 *
 * @param <T> The underlying result type (Symbol, FileEntry, etc.)
 */
public record RankedResult<T>(
    T value,
    double score,
    List<RankingReason> reasons
) {
    public RankedResult {
        reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }

    public static <T> RankedResult<T> of(T value, double score, RankingReason... reasons) {
        return new RankedResult<>(value, score, List.of(reasons));
    }
}
