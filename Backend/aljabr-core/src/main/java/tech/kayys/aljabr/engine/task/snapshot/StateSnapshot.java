package tech.kayys.aljabr.engine.task.snapshot;

import tech.kayys.aljabr.engine.task.TaskState;

public record StateSnapshot(
    int schemaVersion,
    TaskState state,
    ReasoningState reasoning,
    ContextState context,
    WorkspaceState workspace,
    ChangeState changes,
    BudgetState budgets
) {
    public static final int CURRENT_SCHEMA = 1;

    public static StateSnapshot of(TaskState state) {
        return new StateSnapshot(
            CURRENT_SCHEMA,
            state,
            ReasoningState.empty(),
            ContextState.empty(),
            null,
            ChangeState.empty(),
            BudgetState.unlimited()
        );
    }
}
