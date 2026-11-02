package tech.kayys.cifr.engine.capability;

import java.util.Optional;

/**
 * Token signaling cooperative execution cancellation.
 */
public interface CancellationToken {
    boolean isCancelled();
    void cancel(String reason);
    Optional<String> cancellationReason();

    static CancellationToken create() {
        return new DefaultCancellationToken();
    }
}
