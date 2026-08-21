package tech.kayys.aljabr.engine.memory;

/**
 * Epistemic status of a memory item, tracking certainty and verification degree.
 */
public enum MemoryStatus {
    /** Directly observed from tool execution, file reading, or compiler output. */
    OBSERVED,
    
    /** Inferred through agent reasoning or pattern matching. */
    INFERRED,
    
    /** Unverified hypothesis under test. */
    HYPOTHESIS,
    
    /** Directly asserted by the user or explicit prompt instruction. */
    USER_ASSERTED,
    
    /** Formally verified against test suites, linters, or proof checks. */
    VERIFIED,
    
    /** Conflicted or disputed by contradictory observation. */
    DISPUTED,
    
    /** Explicitly superseded, refuted, or invalidated. */
    INVALIDATED
}
