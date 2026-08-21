package tech.kayys.aljabr.engine.runtime.event;

/**
 * Payload recorded when post-crash reconciliation establishes the actual outcome of an interrupted execution.
 */
public record ExecutionReconciledPayload(
    String transactionId,
    String realityStatus,
    String reconciliationEvidenceId
) implements EventPayload {
    @Override
    public String payloadType() {
        return "ExecutionReconciled";
    }
}
