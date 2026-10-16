package tech.kayys.aljabr.engine.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.lifecycle.AgentTask;
import tech.kayys.aljabr.engine.task.TaskId;
import tech.kayys.aljabr.engine.task.TaskGoal;
import tech.kayys.aljabr.engine.task.step.StepStatus;
import tech.kayys.aljabr.engine.task.step.StepType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TaskGraphPlanningTest {

    private DefaultPlanningEngine planningEngine;
    private AgentTask task;

    @BeforeEach
    public void setup() {
        planningEngine = new DefaultPlanningEngine();
        task = AgentTask.create(TaskId.of("t-plan-1"), "ws-1", TaskGoal.of("Refactor auth"));
    }

    @Test
    public void testTaskGraphConstructionAndReadySteps() {
        TaskGraph graph = planningEngine.plan(task, "Refactor auth").await().indefinitely();

        assertNotNull(graph);
        assertEquals(5, graph.allSteps().size());

        // Initially, only the root step (with 0 unsatisfied prerequisites) is ready
        List<TaskStep> readyInitially = graph.getReadySteps();
        assertEquals(1, readyInitially.size());
        assertEquals("step-understand", readyInitially.get(0).id().value());

        // Complete step-understand -> step-retrieve becomes ready
        TaskStep s1 = graph.getStep(TaskStepId.of("step-understand"));
        graph.addStep(s1.withStatus(StepStatus.COMPLETED));

        List<TaskStep> readyAfterS1 = graph.getReadySteps();
        assertEquals(1, readyAfterS1.size());
        assertEquals("step-retrieve", readyAfterS1.get(0).id().value());

        // Complete remaining steps
        graph.addStep(graph.getStep(TaskStepId.of("step-retrieve")).withStatus(StepStatus.COMPLETED));
        graph.addStep(graph.getStep(TaskStepId.of("step-plan")).withStatus(StepStatus.COMPLETED));
        graph.addStep(graph.getStep(TaskStepId.of("step-execute")).withStatus(StepStatus.COMPLETED));
        graph.addStep(graph.getStep(TaskStepId.of("step-verify")).withStatus(StepStatus.COMPLETED));

        assertTrue(graph.isComplete(), "TaskGraph must be complete when all steps are completed");
    }

    @Test
    public void testBranchingDependencies() {
        TaskGraph graph = new TaskGraph();

        TaskStepId root = TaskStepId.of("root");
        TaskStepId branchA = TaskStepId.of("branch-a");
        TaskStepId branchB = TaskStepId.of("branch-b");
        TaskStepId join = TaskStepId.of("join");

        graph.addStep(TaskStep.of(root, "Root", StepType.ANALYZE))
             .addStep(TaskStep.of(branchA, "Branch A", StepType.INVESTIGATE))
             .addStep(TaskStep.of(branchB, "Branch B", StepType.INVESTIGATE))
             .addStep(TaskStep.of(join, "Join", StepType.VERIFY));

        graph.addDependency(root, branchA)
             .addDependency(root, branchB)
             .addDependency(branchA, join)
             .addDependency(branchB, join);

        // Complete root -> both branches ready
        graph.addStep(graph.getStep(root).withStatus(StepStatus.COMPLETED));

        List<TaskStep> readyBranches = graph.getReadySteps();
        assertEquals(2, readyBranches.size());

        // Complete only branchA -> join is NOT ready yet
        graph.addStep(graph.getStep(branchA).withStatus(StepStatus.COMPLETED));
        List<TaskStep> readyBeforeJoin = graph.getReadySteps();
        assertEquals(1, readyBeforeJoin.size());
        assertEquals("branch-b", readyBeforeJoin.get(0).id().value());

        // Complete branchB -> join is now ready
        graph.addStep(graph.getStep(branchB).withStatus(StepStatus.COMPLETED));
        List<TaskStep> readyJoin = graph.getReadySteps();
        assertEquals(1, readyJoin.size());
        assertEquals("join", readyJoin.get(0).id().value());
    }
}
