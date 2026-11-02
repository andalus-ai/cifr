package tech.kayys.cifr.engine.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ResilientModelRouterTest {

    private ResilientModelRouter router;

    @BeforeEach
    public void setup() {
        router = new ResilientModelRouter();
    }

    @Test
    public void testDefaultPrimaryTier() {
        ResilientModelRouter.InferenceTier optimal = router.resolveOptimalTier();
        assertEquals(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, optimal);
    }

    @Test
    public void testCircuitBreakerTripsAndFailsOver() {
        // Record 3 failures on primary tier
        router.recordFailure(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "Connection refused");
        router.recordFailure(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "CUDA out of memory");
        router.recordFailure(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "Timeout");

        // Primary circuit is now OPEN -> router must fail over to Tier 2 (Quantized Fallback)
        ResilientModelRouter.InferenceTier fallback = router.resolveOptimalTier();
        assertEquals(ResilientModelRouter.InferenceTier.LOCAL_QUANTIZED_FALLBACK, fallback);
    }

    @Test
    public void testCircuitRecoveryOnSuccess() {
        router.recordSuccess(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, 85, 42.5);
        ResilientModelRouter.TierHealth health = router.getHealthSummary().get(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA);

        assertEquals(ResilientModelRouter.CircuitState.CLOSED, health.state);
        assertEquals(1, health.successfulRequests.get());
        assertTrue(health.averageLatencyMs > 0);
    }
}
