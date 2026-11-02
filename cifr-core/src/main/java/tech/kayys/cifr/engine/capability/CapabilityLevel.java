package tech.kayys.cifr.engine.capability;

/**
 * Tier level for progressive disclosure of tool and capability schemas.
 */
public enum CapabilityLevel {
    /** L0: Brief token-efficient name and 1-line summary only. */
    L0_INDEX,

    /** L1: Compact parameter overview and primary return type. */
    L1_COMPACT,

    /** L2: Full JSON schema specification with all properties, constraints, and validation rules. */
    L2_FULL_SCHEMA
}
