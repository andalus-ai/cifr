package tech.kayys.cifr.engine.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyEngineAndAggregatorTest {

    private DefaultPolicyAggregator aggregator;

    @BeforeEach
    public void setup() {
        aggregator = new DefaultPolicyAggregator();
    }

    @Test
    public void testStrictPrecedenceOrdering() {
        Decision allow = Decision.allow("Permitted");
        Decision requireApproval = Decision.requireApproval("appr-1", "Needs user consent");
        Decision allowWithLimit = new Decision(DecisionType.ALLOW_WITH_LIMIT, DecisionReason.of("LIMIT", "Max 5 calls"), java.util.Optional.empty(), java.util.Optional.empty());
        Decision deny = Decision.deny("RULE-01", "Denied by security boundary");

        // DENY takes precedence over all
        Decision combined1 = aggregator.combine(List.of(allow, requireApproval, deny));
        assertEquals(DecisionType.DENY, combined1.type());
        assertEquals("RULE-01", combined1.ruleId().orElse(null));

        // REQUIRE_APPROVAL takes precedence over ALLOW_WITH_LIMIT and ALLOW
        Decision combined2 = aggregator.combine(List.of(allow, allowWithLimit, requireApproval));
        assertEquals(DecisionType.REQUIRE_APPROVAL, combined2.type());

        // ALLOW_WITH_LIMIT takes precedence over ALLOW
        Decision combined3 = aggregator.combine(List.of(allow, allowWithLimit));
        assertEquals(DecisionType.ALLOW_WITH_LIMIT, combined3.type());

        // All ALLOW returns ALLOW
        Decision combined4 = aggregator.combine(List.of(allow, allow));
        assertEquals(DecisionType.ALLOW, combined4.type());
    }

    @Test
    public void testEmptyDecisionsDefaultsToAllow() {
        Decision combined = aggregator.combine(List.of());
        assertEquals(DecisionType.ALLOW, combined.type());
        assertTrue(combined.isAllowed());
    }
}
