package tech.kayys.aljabr.engine.action;

import tech.kayys.aljabr.engine.store.Evidence;

/**
 * Extracts structured, typed Evidence observations from raw execution observations.
 */
public interface EvidenceExtractor {

    Evidence extract(String executionId, Observation observation);
}
