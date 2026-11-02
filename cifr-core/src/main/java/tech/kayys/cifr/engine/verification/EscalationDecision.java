package tech.kayys.cifr.engine.verification;

/**
 * Decision returned after evaluating a verification stage result.
 */
public enum EscalationDecision {
    /** Stage passed with sufficient confidence — stop verification and commit. */
    STOP_SUCCESS,
    /** Stage failed — route to repair loop with structured diagnostics. */
    REPAIR,
    /** Stage passed but risk requires escalating to a deeper verification stage. */
    ESCALATE,
    /** Stage could not conclude (e.g. environment issue) — route to investigation. */
    INCONCLUSIVE
}
