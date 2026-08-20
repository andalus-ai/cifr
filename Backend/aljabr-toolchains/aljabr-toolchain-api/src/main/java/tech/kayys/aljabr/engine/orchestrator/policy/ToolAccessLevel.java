package tech.kayys.aljabr.engine.orchestrator.policy;

/**
 * Levels at which a tool is permitted to operate.
 * Aligned with the phase access controls described in enhance-10.
 */
public enum ToolAccessLevel {
    /** Tool may only read workspace data — no mutations. */
    READ_ONLY,
    /** Tool may mutate files in the local workspace. */
    WORKSPACE_MUTATION,
    /** Tool may produce external side-effects (network, git push, publish, deploy). */
    EXTERNAL_EFFECT
}
