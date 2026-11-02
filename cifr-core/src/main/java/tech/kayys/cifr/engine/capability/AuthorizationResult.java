package tech.kayys.cifr.engine.capability;

/**
 * Decision returned by {@link CapabilityAuthorizer}.
 */
public record AuthorizationResult(
    boolean authorized,
    String reason,
    boolean confirmationRequired
) {
    public static AuthorizationResult allow() {
        return new AuthorizationResult(true, "Authorized by policy", false);
    }

    public static AuthorizationResult allowWithConfirmation(String reason) {
        return new AuthorizationResult(true, reason, true);
    }

    public static AuthorizationResult deny(String reason) {
        return new AuthorizationResult(false, reason, false);
    }
}
