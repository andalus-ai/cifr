package tech.kayys.cifr.engine.lifecycle;

/**
 * Levels of side effect an operation can produce, governing retryability and authorization.
 */
public enum SideEffectLevel {
    /** Pure read/compute operation with zero filesystem or network side effects. Retryable freely. */
    NONE,
    /** Workspace modification that can be rolled back via checkpoints/transactions. */
    REVERSIBLE,
    /** Permanent external side effect (e.g. git push, message send, deploy). Requires explicit approval. */
    IRREVERSIBLE
}
