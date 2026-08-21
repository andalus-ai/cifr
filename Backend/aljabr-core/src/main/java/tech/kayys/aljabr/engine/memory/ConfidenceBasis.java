package tech.kayys.aljabr.engine.memory;

/**
 * Concrete basis for calculating memory confidence score.
 */
public enum ConfidenceBasis {
    USER_STATED,
    TOOL_VERIFIED,
    REPEATED_OBSERVATION,
    MODEL_INFERENCE,
    EXTERNAL_SOURCE
}
