package tech.kayys.aljabr.engine.model;

/**
 * Privacy governance level restricting which model tiers may process a request.
 */
public enum PrivacyRequirement {
    /** Strictly local hardware only (e.g. Apple Silicon Metal / NVIDIA GPU on-premise). */
    LOCAL_ONLY,

    /** Private isolated enterprise cluster / sovereign gateway permitted. */
    PRIVATE_PROVIDER_ONLY,

    /** Public and commercial cloud LLM providers permitted. */
    CLOUD_ALLOWED
}
