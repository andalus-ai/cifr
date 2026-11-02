package tech.kayys.cifr.engine.memory;

import java.util.List;

/**
 * Outcome of executing an intentional forgetting operation.
 */
public record ForgetResult(
    String memoryId,
    ForgetMode mode,
    boolean success,
    List<String> cascadedMemoryIds,
    String message
) {
    public ForgetResult {
        cascadedMemoryIds = cascadedMemoryIds != null ? List.copyOf(cascadedMemoryIds) : List.of();
    }

    public static ForgetResult success(String memoryId, ForgetMode mode, List<String> cascaded, String msg) {
        return new ForgetResult(memoryId, mode, true, cascaded, msg);
    }

    public static ForgetResult failure(String memoryId, ForgetMode mode, String reason) {
        return new ForgetResult(memoryId, mode, false, List.of(), reason);
    }
}
