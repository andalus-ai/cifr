package tech.kayys.aljabr.engine.orchestrator.policy;

/**
 * Decision made by the ToolExecutionPolicy for a given tool request.
 */
public enum ToolDecision {
    /** Allow the tool to execute as-is. */
    ALLOW,
    /** Deny the tool request entirely. */
    DENY,
    /** Require explicit user or system approval before execution. */
    REQUIRE_APPROVAL,
    /** Allow execution but with constrained budget / scope limits. */
    ALLOW_WITH_LIMITS,
    /** Redirect the intent to a safer or more appropriate tool. */
    REDIRECT
}
