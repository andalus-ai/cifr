package tech.kayys.cifr.engine.loop;

/**
 * Action type for the next step scheduled by the AgentScheduler.
 */
public enum NextStepType {
    RUN_DECISION_CYCLE,
    EXECUTE_PENDING_ACTION,
    WAIT_FOR_USER,
    WAIT_FOR_RESOURCE,
    VERIFY,
    REPLAN,
    COMPLETE,
    FAIL
}
