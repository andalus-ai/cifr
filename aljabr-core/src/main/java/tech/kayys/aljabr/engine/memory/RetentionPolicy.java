package tech.kayys.aljabr.engine.memory;

/**
 * Policy governing retention duration and automatic cleanup of memories.
 */
public enum RetentionPolicy {
    /** Retained only for the duration of the current turn or sub-step. */
    EPHEMERAL,
    
    /** Retained for the duration of the agent session. */
    SESSION,
    
    /** Retained until the parent task reaches completion or failure. */
    UNTIL_TASK_END,
    
    /** Retained indefinitely until explicitly contradicted or invalidated. */
    UNTIL_INVALIDATED,
    
    /** Persisted long-term across sessions and projects (e.g. user preferences, codebase invariants). */
    LONG_TERM
}
