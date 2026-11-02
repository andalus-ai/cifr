package tech.kayys.cifr.engine.debug;

import java.util.UUID;

public record ExperimentId(String value) {
    public static ExperimentId generate() {
        return new ExperimentId("exp-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
