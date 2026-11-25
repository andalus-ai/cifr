package tech.kayys.cifr.engine.action;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.policy.Decision;
import tech.kayys.cifr.engine.policy.PolicyContext;

/**
 * Fail-closed authorization fallback.
 *
 * A proposal is not authorization evidence. Until an explicit policy evaluator is
 * wired into the runtime, this implementation denies both initial authorization
 * and reauthorization rather than silently granting actions.
 */
@ApplicationScoped
public class DefaultAuthorizationService implements AuthorizationService {

    @Override
    public Uni<AuthorizationResult> authorize(ActionProposal proposal, PolicyContext context) {
        return Uni.createFrom().item(() -> {
            if (proposal == null) {
                return AuthorizationResult.denied(Decision.deny("NULL_PROPOSAL", "Proposal is null"));
            }
            return AuthorizationResult.denied(Decision.deny(
                "NO_POLICY_EVALUATOR",
                "No authorization policy evaluator is configured"
            ));
        });
    }

    @Override
    public Uni<AuthorizationResult> reauthorize(AuthorizedAction action, PolicyContext context) {
        return Uni.createFrom().item(() -> {
            if (action == null || action.isExpired()) {
                return AuthorizationResult.denied(Decision.deny("EXPIRED", "Authorization is missing or has expired"));
            }
            return AuthorizationResult.denied(Decision.deny(
                "NO_POLICY_EVALUATOR",
                "Authorization cannot be renewed without policy evaluation"
            ));
        });
    }
}
