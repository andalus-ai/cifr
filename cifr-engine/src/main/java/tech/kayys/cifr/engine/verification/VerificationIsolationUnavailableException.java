package tech.kayys.cifr.engine.verification;

/** Raised when verification cannot run without violating required isolation policy. */
public class VerificationIsolationUnavailableException extends RuntimeException {

    public VerificationIsolationUnavailableException(String message) {
        super(message);
    }
}
