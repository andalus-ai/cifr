package tech.kayys.aljabr.engine.planning;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.lifecycle.AgentTask;
import tech.kayys.aljabr.engine.task.step.StepType;

/**
 * Default implementation of {@link PlanningEngine}.
 * Decomposes high-level goals into progressive, dependency-linked steps.
 */
@ApplicationScoped
public class DefaultPlanningEngine implements PlanningEngine {

    @Override
    public Uni<TaskGraph> plan(AgentTask task, String goalDescription) {
        return Uni.createFrom().item(() -> {
            TaskGraph graph = new TaskGraph();

            TaskStepId s1 = TaskStepId.of("step-understand");
            TaskStepId s2 = TaskStepId.of("step-retrieve");
            TaskStepId s3 = TaskStepId.of("step-plan");
            TaskStepId s4 = TaskStepId.of("step-execute");
            TaskStepId s5 = TaskStepId.of("step-verify");

            graph.addStep(TaskStep.of(s1, "Understand task requirements", StepType.ANALYZE))
                 .addStep(TaskStep.of(s2, "Retrieve repository evidence", StepType.INVESTIGATE))
                 .addStep(TaskStep.of(s3, "Formulate patch plan", StepType.PLAN))
                 .addStep(TaskStep.of(s4, "Apply planned mutation", StepType.PATCH))
                 .addStep(TaskStep.of(s5, "Verify regression tests", StepType.VERIFY));

            graph.addDependency(s1, s2)
                 .addDependency(s2, s3)
                 .addDependency(s3, s4)
                 .addDependency(s4, s5);

            return graph;
        });
    }
}
