package tech.kayys.cifr.engine.task.step;

import io.smallrye.mutiny.Uni;

public interface StepExecutor {
    StepType stepType();
    Uni<StepResult> execute(AgentStep step);
}
