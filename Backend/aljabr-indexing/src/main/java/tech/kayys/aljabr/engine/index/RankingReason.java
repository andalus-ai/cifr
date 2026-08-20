package tech.kayys.aljabr.engine.index;

import java.util.List;

/**
 * Reason why a result was ranked at its score.
 */
public enum RankingReason {
    EXACT_NAME_MATCH,
    QUALIFIED_NAME_MATCH,
    SYMBOL_REFERENCE,
    TEST_ASSOCIATION,
    DIRECT_CALLER,
    DEPENDENCY_PROXIMITY,
    PATH_PROXIMITY,
    RECENTLY_MODIFIED,
    PUBLIC_API,
    CONFIGURATION_FILE,
    DOCUMENTATION
}
