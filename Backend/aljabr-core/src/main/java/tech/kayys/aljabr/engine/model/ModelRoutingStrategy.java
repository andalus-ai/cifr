package tech.kayys.aljabr.engine.model;

/**
 * Strategy guiding multi-tier model selection and fallback escalation.
 */
public enum ModelRoutingStrategy {
    /** Prefer local execution, escalating to remote cluster only on capability failure or timeout. */
    LOCAL_FIRST,

    /** Route directly to highest-capability reasoning models regardless of cost or latency. */
    QUALITY_FIRST,

    /** Optimize for lowest financial cost per token. */
    COST_FIRST,

    /** Optimize for fastest time-to-first-token and throughput. */
    LATENCY_FIRST,

    /** Enforce strict local containment with no cloud data transmission. */
    PRIVACY_FIRST,

    /** Use explicitly specified model identifier or tier. */
    EXPLICIT
}
