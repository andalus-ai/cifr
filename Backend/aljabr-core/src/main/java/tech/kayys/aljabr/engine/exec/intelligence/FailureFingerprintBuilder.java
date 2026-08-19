package tech.kayys.aljabr.engine.exec.intelligence;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FailureFingerprintBuilder {

    public FailureFingerprint build(FailureAnalysis analysis) {
        return FailureFingerprint.of(analysis);
    }
}
