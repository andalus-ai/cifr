package tech.kayys.cifr.engine.task.runtime;

import tech.kayys.cifr.engine.task.TaskState;

public interface TransitionPolicy {
    TransitionDecision evaluate(TaskState current, AgentSignal signal);
}
