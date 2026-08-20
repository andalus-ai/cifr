package tech.kayys.aljabr.engine.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extension SPI for providing custom enterprise security policies and guardrails.
 */
public interface SecurityPolicyProvider {

    /**
     * Additional blocked path prefixes.
     */
    default Set<String> blockedPathPrefixes() {
        return Set.of();
    }

    /**
     * Additional sensitive filename patterns.
     */
    default Set<String> sensitiveFilenamePatterns() {
        return Set.of();
    }

    /**
     * Additional destructive command regex patterns.
     */
    default List<Pattern> destructiveCommandPatterns() {
        return List.of();
    }

    /**
     * Custom evaluation hook. If returns non-empty, this decision takes precedence.
     */
    default Optional<EnterpriseSecurityGuardrails.PolicyEvaluation> customEvaluate(String command, String workingDirectory) {
        return Optional.empty();
    }
}
