package tech.kayys.cifr.engine.action;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.policy.PolicyContext;

/**
 * Service managing proposal validation, authorization, and time-limited execution token issuance.
 */
public interface AuthorizationService {

    Uni<AuthorizationResult> authorize(ActionProposal proposal, PolicyContext context);

    Uni<AuthorizationResult> reauthorize(AuthorizedAction action, PolicyContext context);
}
