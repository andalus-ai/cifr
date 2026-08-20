package tech.kayys.aljabr.engine.policy;

import java.util.List;

/**
 * Combines multiple policy evaluation results using the strongest constraint rule:
 * DENY > REQUIRE_APPROVAL > ALLOW_WITH_LIMIT > ALLOW.
 */
public interface PolicyAggregator {

    Decision combine(List<Decision> decisions);
}
