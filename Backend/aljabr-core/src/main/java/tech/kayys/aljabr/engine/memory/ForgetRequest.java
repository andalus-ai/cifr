package tech.kayys.aljabr.engine.memory;

import java.util.Optional;

/**
 * Request to forget, revoke, or redact a specific memory item or scope.
 */
public record ForgetRequest(
    String memoryId,
    String reason,
    ForgetMode mode,
    boolean cascadeDerivedMemories,
    Optional<String> supersedingMemoryId
) {
    public static ForgetRequest delete(String memoryId, String reason) {
        return new ForgetRequest(memoryId, reason, ForgetMode.DELETE, true, Optional.empty());
    }

    public static ForgetRequest invalidate(String memoryId, String reason) {
        return new ForgetRequest(memoryId, reason, ForgetMode.INVALIDATE, true, Optional.empty());
    }

    public static ForgetRequest supersede(String memoryId, String supersedingId, String reason) {
        return new ForgetRequest(memoryId, reason, ForgetMode.SUPERSEDE, true, Optional.of(supersedingId));
    }

    public static ForgetRequest redact(String memoryId, String reason) {
        return new ForgetRequest(memoryId, reason, ForgetMode.REDACT, false, Optional.empty());
    }
}
