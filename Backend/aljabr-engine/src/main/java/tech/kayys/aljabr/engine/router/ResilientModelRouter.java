package tech.kayys.aljabr.engine.router;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Resilient Model Router & Circuit Breaker.
 * Dynamically routes inference requests across local hardware acceleration,
 * quantized fallback models, and remote GPU clusters with auto-failover and latency tracking.
 */
@ApplicationScoped
public class ResilientModelRouter {

    private static final Logger LOG = Logger.getLogger(ResilientModelRouter.class);

    public enum CircuitState {
        CLOSED,     // Healthy, traffic flowing
        OPEN,       // Unhealthy, traffic diverted to fallback
        HALF_OPEN   // Probing recovery
    }

    public enum InferenceTier {
        LOCAL_PRIMARY_METAL_CUDA("Local Hardware Accelerated Gollek", "http://localhost:8080", 9090, 1),
        LOCAL_QUANTIZED_FALLBACK("Local Quantized 4-Bit Substrate", "http://localhost:8082", 9092, 2),
        ENTERPRISE_REMOTE_CLUSTER("Enterprise Remote GPU Worker Pool", "http://gpu-cluster.internal:8080", 9090, 3),
        SOVEREIGN_CLOUD_GATEWAY("Sovereign Cloud LLM Gateway", "http://gateway.internal:8000", 8000, 4);

        public final String displayName;
        public final String defaultHttpUrl;
        public final int defaultGrpcPort;
        public final int priority;

        InferenceTier(String displayName, String defaultHttpUrl, int defaultGrpcPort, int priority) {
            this.displayName = displayName;
            this.defaultHttpUrl = defaultHttpUrl;
            this.defaultGrpcPort = defaultGrpcPort;
            this.priority = priority;
        }
    }

    public static class TierHealth {
        public CircuitState state = CircuitState.CLOSED;
        public final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        public final AtomicInteger totalRequests = new AtomicInteger(0);
        public final AtomicInteger successfulRequests = new AtomicInteger(0);
        public long lastFailureTimestamp = 0;
        public double averageLatencyMs = 0.0;
        public double lastTokensPerSecond = 0.0;
    }

    private final Map<InferenceTier, TierHealth> tierHealthMap = new ConcurrentHashMap<>();
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long RECOVERY_TIMEOUT_MS = 30_000; // 30 seconds

    public ResilientModelRouter() {
        for (InferenceTier tier : InferenceTier.values()) {
            tierHealthMap.put(tier, new TierHealth());
        }
    }

    /**
     * Resolves the optimal active inference tier based on real-time circuit health.
     */
    public InferenceTier resolveOptimalTier() {
        long now = System.currentTimeMillis();

        for (InferenceTier tier : List.of(
                InferenceTier.LOCAL_PRIMARY_METAL_CUDA,
                InferenceTier.LOCAL_QUANTIZED_FALLBACK,
                InferenceTier.ENTERPRISE_REMOTE_CLUSTER,
                InferenceTier.SOVEREIGN_CLOUD_GATEWAY)) {

            TierHealth health = tierHealthMap.get(tier);

            if (health.state == CircuitState.OPEN) {
                if (now - health.lastFailureTimestamp > RECOVERY_TIMEOUT_MS) {
                    LOG.infof("Tier [%s] circuit breaker transitioning from OPEN to HALF_OPEN probe.", tier.name());
                    health.state = CircuitState.HALF_OPEN;
                    return tier;
                }
                // Skip open circuit and try next fallback tier
                continue;
            }

            return tier;
        }

        // Default fallback to primary if all are tripping
        return InferenceTier.LOCAL_PRIMARY_METAL_CUDA;
    }

    /**
     * Records a successful inference completion on a tier.
     */
    public void recordSuccess(InferenceTier tier, long latencyMs, double tokensPerSecond) {
        TierHealth health = tierHealthMap.get(tier);
        if (health != null) {
            health.consecutiveFailures.set(0);
            health.state = CircuitState.CLOSED;
            int total = health.totalRequests.incrementAndGet();
            health.successfulRequests.incrementAndGet();

            // Exponential moving average for latency
            health.averageLatencyMs = (health.averageLatencyMs * 0.8) + (latencyMs * 0.2);
            health.lastTokensPerSecond = tokensPerSecond;
        }
    }

    /**
     * Records an inference failure or timeout on a tier.
     */
    public void recordFailure(InferenceTier tier, String errorReason) {
        TierHealth health = tierHealthMap.get(tier);
        if (health != null) {
            health.totalRequests.incrementAndGet();
            health.lastFailureTimestamp = System.currentTimeMillis();
            int failures = health.consecutiveFailures.incrementAndGet();

            if (failures >= MAX_CONSECUTIVE_FAILURES && health.state != CircuitState.OPEN) {
                health.state = CircuitState.OPEN;
                LOG.warnf("⚠️ Circuit Breaker TRIPPED for tier [%s] after %d failures: %s. Traffic diverting to fallback tier.",
                        tier.name(), failures, errorReason);
            }
        }
    }

    public Map<InferenceTier, TierHealth> getHealthSummary() {
        return Map.copyOf(tierHealthMap);
    }
}
