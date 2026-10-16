package tech.kayys.aljabr.engine.memory;

import java.util.List;

/**
 * Deterministic ranker ordering memory matches by composite score, recency, and scope proximity.
 */
public interface MemoryRanker {

    List<MemoryMatch> rank(List<MemoryMatch> matches);
}
