package tech.kayys.aljabr.engine.action;

/**
 * Generic action types supported by the Wayang runtime.
 */
public enum ActionType {
    READ,
    SEARCH,
    COMPUTE,
    TRANSFORM,
    MODIFY,
    DELETE,
    CREATE,
    INVOKE,
    EXTERNAL_SIDE_EFFECT
}
