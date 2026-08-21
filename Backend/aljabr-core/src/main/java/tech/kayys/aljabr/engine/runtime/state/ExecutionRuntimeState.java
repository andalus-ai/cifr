package tech.kayys.aljabr.engine.runtime.state;

import tech.kayys.aljabr.engine.action.ActionResultStatus;

import java.util.Optional;

/**
 * Materialized execution transaction state.
 */
public record ExecutionRuntimeState(
    String transactionId,
    String actionId,
    String state,
    Optional<ActionResultStatus> resultStatus,
    boolean isReconciled
) {
    public static ExecutionRuntimeState start(String txId, String actionId) {
        return new ExecutionRuntimeState(txId, actionId, "RUNNING", Optional.empty(), false);
    }

    public ExecutionRuntimeState complete(ActionResultStatus status) {
        return new ExecutionRuntimeState(transactionId, actionId, "COMPLETED", Optional.of(status), isReconciled);
    }

    public ExecutionRuntimeState reconcile(String realityStatus) {
        return new ExecutionRuntimeState(transactionId, actionId, "RECONCILED: " + realityStatus, resultStatus, true);
    }
}
