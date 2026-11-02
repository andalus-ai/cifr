package tech.kayys.cifr.engine.memory;

/**
 * Execution mode for decommissioning or modifying forgotten memories.
 */
public enum ForgetMode {
    /** Hard delete of the memory record and associated embeddings. */
    DELETE,

    /** Soft invalidate: marks epistemic status as INVALIDATED and revokes validity. */
    INVALIDATE,

    /** Replaces the memory with a pointer to the superseding memory ID. */
    SUPERSEDE,

    /** Replaces sensitive content with redaction markers while retaining structural metadata. */
    REDACT
}
