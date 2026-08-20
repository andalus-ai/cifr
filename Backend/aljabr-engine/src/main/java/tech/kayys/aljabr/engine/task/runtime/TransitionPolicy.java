package tech.kayys.aljabr.engine.task.runtime;

import tech.kayys.aljabr.engine.task.TaskState;

public interface TransitionPolicy {
    TransitionDecision evaluate(TaskState current, AgentSignal signal);
}
