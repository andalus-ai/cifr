package tech.kayys.cifr.engine.action;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.policy.Decision;
import tech.kayys.cifr.engine.policy.PolicyContext;

import java.util.UUID;

/**
 * Default implementation of {@link AuthorizationService}.
 */
@ApplicationScoped
public class DefaultAuthorizationService implements AuthorizationService {

    @Override
    public Uni<AuthorizationResult> authorize(ActionProposal proposal, PolicyContext context) {
        return Uni.createFrom().item(() -> {
            if (proposal == null) {
                return AuthorizationResult.denied(Decision.deny("NULL_PROPOSAL", "Proposal is null"));
            }
            String actionId = "act-" + UUID.randomUUID();
            AuthorizedAction authorized = AuthorizedAction.of(actionId, proposal);
            return AuthorizationResult.granted(authorized, Decision.allow("Authorized by default policy"));
        });
    }

    @Override
    public Uni<AuthorizationResult> reauthorize(AuthorizedAction action, PolicyContext context) {
        return Uni.createFrom().item(() -> {
            if (action == null || action.isExpired()) {
                return AuthorizationResult.denied(Decision.deny("EXPIRED", "Authorization has expired"));
            }
            return AuthorizationResult.granted(action, Decision.allow("Reauthorized"));
        });
    }
}
