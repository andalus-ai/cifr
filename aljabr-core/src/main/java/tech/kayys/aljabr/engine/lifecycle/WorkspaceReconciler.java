package tech.kayys.aljabr.engine.lifecycle;

import tech.kayys.aljabr.engine.patch.transaction.WorkspaceCheckpoint;

/**
 * Reconciles the state of a workspace after an unexpected shutdown or process interruption.
 */
public interface WorkspaceReconciler {

    WorkspaceConsistency reconcile(WorkspaceCheckpoint checkpoint);
}
