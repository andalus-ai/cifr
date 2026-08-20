package tech.kayys.aljabr.engine.task.step;

import io.smallrye.mutiny.Uni;

public interface StepExecutor {
    StepType stepType();
    Uni<StepResult> execute(AgentStep step);
}
