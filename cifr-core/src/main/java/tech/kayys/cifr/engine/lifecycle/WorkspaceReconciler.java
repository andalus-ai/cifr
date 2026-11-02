package tech.kayys.cifr.engine.lifecycle;

import tech.kayys.cifr.engine.patch.transaction.WorkspaceCheckpoint;

/**
 * Reconciles the state of a workspace after an unexpected shutdown or process interruption.
 */
public interface WorkspaceReconciler {

    WorkspaceConsistency reconcile(WorkspaceCheckpoint checkpoint);
}
