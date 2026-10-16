package tech.kayys.aljabr.engine.memory;

import tech.kayys.aljabr.engine.store.Memory;

import java.util.List;

/**
 * Ranked memory search result with cumulative score and explicit match reasons.
 */
public record MemoryMatch(
    Memory memory,
    double score,
    List<MatchReason> reasons
) {
    public MemoryMatch {
        reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }

    public static MemoryMatch of(Memory memory, double score, List<MatchReason> reasons) {
        return new MemoryMatch(memory, score, reasons);
    }
}
