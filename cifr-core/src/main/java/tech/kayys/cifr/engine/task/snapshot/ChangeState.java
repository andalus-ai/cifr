package tech.kayys.cifr.engine.task.snapshot;

import java.util.List;

public record ChangeState(
    List<String> pendingChangeIds,
    List<String> appliedChangeIds
) {
    public ChangeState {
        pendingChangeIds = pendingChangeIds != null ? List.copyOf(pendingChangeIds) : List.of();
        appliedChangeIds = appliedChangeIds != null ? List.copyOf(appliedChangeIds) : List.of();
    }

    public static ChangeState empty() {
        return new ChangeState(List.of(), List.of());
    }
}
