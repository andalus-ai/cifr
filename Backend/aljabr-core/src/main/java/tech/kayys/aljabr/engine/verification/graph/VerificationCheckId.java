package tech.kayys.aljabr.engine.verification.graph;

public record VerificationCheckId(String value) {
    public static VerificationCheckId of(String value) {
        return new VerificationCheckId(value);
    }
}
