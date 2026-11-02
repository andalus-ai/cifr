package tech.kayys.cifr.engine.lifecycle;

import tech.kayys.cifr.engine.task.TaskId;

/**
 * Narrow, secure context provided to state handlers during lifecycle execution.
 * Avoids exposing raw filesystem paths or unrestricted runtime access.
 */
public interface AgentStateContext {

    AgentTask task();

    TaskId taskId();

    AgentState state();
}
