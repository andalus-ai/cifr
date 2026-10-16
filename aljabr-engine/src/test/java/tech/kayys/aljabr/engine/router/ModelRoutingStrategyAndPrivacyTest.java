package tech.kayys.aljabr.engine.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.model.EscalationReason;
import tech.kayys.aljabr.engine.model.ModelRequirements;
import tech.kayys.aljabr.engine.model.ModelRoutingStrategy;
import tech.kayys.aljabr.engine.model.PrivacyRequirement;

import static org.junit.jupiter.api.Assertions.*;

public class ModelRoutingStrategyAndPrivacyTest {

    private ResilientModelRouter router;

    @BeforeEach
    void setUp() {
        router = new ResilientModelRouter();
    }

    @Test
    void testLocalFirstStrategyRoutesToHardwareAcceleratedGollek() {
        ModelRequirements req = ModelRequirements.standardLocal();
        ResilientModelRouter.RoutingDecision decision = router.route(req);

        assertEquals(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, decision.tier());
        assertFalse(decision.escalated());
        assertEquals(EscalationReason.NONE, decision.escalationReason());
    }

    @Test
    void testPrivacyFirstStrictlyConstrainsToLocalTiers() {
        ModelRequirements req = ModelRequirements.privateLocalOnly();
        ResilientModelRouter.RoutingDecision decision = router.route(req);

        assertTrue(decision.tier().isLocal);
    }

    @Test
    void testContextExceededEscalationWhenCloudAllowed() {
        ModelRequirements largeContextReq = new ModelRequirements(
            32000L, true, true, false, false, PrivacyRequirement.CLOUD_ALLOWED, ModelRoutingStrategy.LOCAL_FIRST
        );

        ResilientModelRouter.RoutingDecision decision = router.route(largeContextReq);
        assertEquals(ResilientModelRouter.InferenceTier.ENTERPRISE_REMOTE_CLUSTER, decision.tier());
        assertTrue(decision.escalated());
        assertEquals(EscalationReason.CONTEXT_LIMIT_EXCEEDED, decision.escalationReason());
    }

    @Test
    void testCircuitBreakerTrippingEscalatesToNextTier() {
        // Record 3 consecutive failures on primary tier to trip circuit breaker
        router.recordFailure(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "GPU out of memory");
        router.recordFailure(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "GPU out of memory");
        router.recordFailure(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "GPU out of memory");

        ModelRequirements req = ModelRequirements.standardLocal();
        ResilientModelRouter.RoutingDecision decision = router.route(req);

        assertEquals(ResilientModelRouter.InferenceTier.LOCAL_QUANTIZED_FALLBACK, decision.tier());
        assertTrue(decision.escalated());
        assertEquals(EscalationReason.CIRCUIT_BREAKER_OPEN, decision.escalationReason());
    }
}
