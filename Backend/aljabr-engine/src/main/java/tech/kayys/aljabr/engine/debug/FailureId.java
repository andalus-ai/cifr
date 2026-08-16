package tech.kayys.aljabr.engine.debug;

import java.util.UUID;

public record FailureId(String value) {
    public static FailureId generate() {
        return new FailureId("fail-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
