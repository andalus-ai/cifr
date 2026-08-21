package tech.kayys.aljabr.engine.planning;

import tech.kayys.aljabr.engine.task.step.StepStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * Directed acyclic graph of steps representing a planned task execution workflow.
 */
public final class TaskGraph {

    private final Map<TaskStepId, TaskStep> steps = new ConcurrentHashMap<>();
    private final Set<TaskDependency> dependencies = new CopyOnWriteArraySet<>();

    public TaskGraph() {}

    public TaskGraph addStep(TaskStep step) {
        steps.put(step.id(), step);
        return this;
    }

    public TaskGraph addDependency(TaskStepId from, TaskStepId to) {
        dependencies.add(TaskDependency.of(from, to));
        return this;
    }

    public TaskStep getStep(TaskStepId id) {
        return steps.get(id);
    }

    public List<TaskStep> allSteps() {
        return new ArrayList<>(steps.values());
    }

    public List<TaskStep> getReadySteps() {
        return steps.values().stream()
            .filter(step -> step.status() == StepStatus.PENDING || step.status() == StepStatus.READY)
            .filter(this::areDependenciesSatisfied)
            .collect(Collectors.toList());
    }

    public boolean isComplete() {
        return steps.values().stream()
            .allMatch(step -> step.status() == StepStatus.COMPLETED || step.status() == StepStatus.SKIPPED);
    }

    private boolean areDependenciesSatisfied(TaskStep step) {
        Set<TaskStepId> prereqs = dependencies.stream()
            .filter(dep -> dep.toStep().equals(step.id()))
            .map(TaskDependency::fromStep)
            .collect(Collectors.toSet());

        return prereqs.stream().allMatch(pId -> {
            TaskStep prereq = steps.get(pId);
            return prereq != null && prereq.status() == StepStatus.COMPLETED;
        });
    }
}
