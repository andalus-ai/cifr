package tech.kayys.aljabr.engine.policy;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Default implementation of {@link PolicyAggregator}.
 * Aggregates decisions with strict precedence:
 * DENY > REQUIRE_APPROVAL > ALLOW_WITH_LIMIT > DEFER > FALLBACK > ALLOW.
 */
@ApplicationScoped
public class DefaultPolicyAggregator implements PolicyAggregator {

    @Override
    public Decision combine(List<Decision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return Decision.allow("No policy restrictions");
        }

        for (Decision d : decisions) {
            if (d.type() == DecisionType.DENY) {
                return d;
            }
        }
        for (Decision d : decisions) {
            if (d.type() == DecisionType.REQUIRE_APPROVAL) {
                return d;
            }
        }
        for (Decision d : decisions) {
            if (d.type() == DecisionType.ALLOW_WITH_LIMIT) {
                return d;
            }
        }
        for (Decision d : decisions) {
            if (d.type() == DecisionType.DEFER) {
                return d;
            }
        }
        for (Decision d : decisions) {
            if (d.type() == DecisionType.FALLBACK) {
                return d;
            }
        }

        return Decision.allow("All policies cleared");
    }
}
