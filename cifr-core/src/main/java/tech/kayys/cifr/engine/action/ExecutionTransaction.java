package tech.kayys.cifr.engine.action;

import java.time.Instant;

/**
 * An active execution transaction wrapping an authorized action.
 */
public record ExecutionTransaction(
    String transactionId,
    AuthorizedAction action,
    String state,
    Instant startedAt
) {
    public static ExecutionTransaction start(String transactionId, AuthorizedAction action) {
        return new ExecutionTransaction(transactionId, action, "EXECUTING", Instant.now());
    }
}
