package tech.kayys.aljabr.engine.policy;

import io.smallrye.mutiny.Uni;

/**
 * Central engine evaluating policy requests against active security, budget, and access rules.
 */
public interface PolicyEngine {

    Uni<Decision> evaluate(PolicyContext context, PolicyRequest request);
}
