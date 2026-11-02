package tech.kayys.cifr.engine.exec.intelligence;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FailureFingerprintBuilder {

    public FailureFingerprint build(FailureAnalysis analysis) {
        return FailureFingerprint.of(analysis);
    }
}
