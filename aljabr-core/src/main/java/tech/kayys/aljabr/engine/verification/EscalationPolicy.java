package tech.kayys.aljabr.engine.verification;

import tech.kayys.aljabr.engine.patch.RiskLevel;

/**
 * Policy deciding whether to finish, repair, or escalate to a deeper verification stage.
 */
public interface EscalationPolicy {

    EscalationDecision decide(
        VerificationResult result,
        RiskLevel risk,
        VerificationBudget budget,
        int currentStageIndex
    );
}
