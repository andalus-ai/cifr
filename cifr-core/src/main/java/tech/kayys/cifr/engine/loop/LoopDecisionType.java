package tech.kayys.cifr.engine.loop;

/**
 * Finite decision vocabulary chosen by the model in an atomic reasoning cycle.
 */
public enum LoopDecisionType {
    ACT,
    RETRIEVE,
    ASK_USER,
    RESPOND,
    REPLAN,
    WAIT,
    COMPLETE,
    FAIL
}
