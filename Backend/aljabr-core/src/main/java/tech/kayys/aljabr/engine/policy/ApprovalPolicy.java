package tech.kayys.aljabr.engine.policy;

/**
 * Policy deciding whether an action proposal requires explicit user confirmation.
 */
public interface ApprovalPolicy {

    ApprovalDecision evaluate(PolicyContext context, String actionSummary);
}
