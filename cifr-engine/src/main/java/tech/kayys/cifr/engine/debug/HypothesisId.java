package tech.kayys.cifr.engine.debug;

import java.util.UUID;

public record HypothesisId(String value) {
    public static HypothesisId generate() {
        return new HypothesisId("hyp-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
