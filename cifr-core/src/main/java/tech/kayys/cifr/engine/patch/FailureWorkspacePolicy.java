package tech.kayys.cifr.engine.patch;

/**
 * Policy defining what to do with the workspace when verification fails after patch application.
 */
public enum FailureWorkspacePolicy {
    /** Rollback the applied patch immediately to restore clean state. */
    ROLLBACK,
    /** Preserve the modified workspace (useful for debugging and investigation tasks). */
    PRESERVE,
    /** Checkpoint the current state and allow continuing in-place repair. */
    CHECKPOINT_AND_CONTINUE
}
