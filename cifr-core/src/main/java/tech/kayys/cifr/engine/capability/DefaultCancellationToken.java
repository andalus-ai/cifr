package tech.kayys.cifr.engine.capability;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultCancellationToken implements CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<String> reason = new AtomicReference<>(null);

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel(String cancelReason) {
        if (cancelled.compareAndSet(false, true)) {
            reason.set(cancelReason != null ? cancelReason : "Cancelled by caller");
        }
    }

    @Override
    public Optional<String> cancellationReason() {
        return Optional.ofNullable(reason.get());
    }
}
