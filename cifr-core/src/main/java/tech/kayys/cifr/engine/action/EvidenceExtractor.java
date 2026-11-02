package tech.kayys.cifr.engine.action;

import tech.kayys.cifr.engine.store.Evidence;

/**
 * Extracts structured, typed Evidence observations from raw execution observations.
 */
public interface EvidenceExtractor {

    Evidence extract(String executionId, Observation observation);
}
