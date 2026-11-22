package tech.kayys.cifr.engine.verification;

import tech.kayys.cifr.engine.orchestrator.toolchain.BuildSystem;

import java.nio.file.Path;
import java.util.List;

/**
 * Deterministic plan for project verification. A plan is not execution evidence:
 * checks must still be run by an authorized execution backend.
 */
public record VerificationPlan(
        BuildSystem buildSystem,
        Path workspaceRoot,
        List<VerificationCheckDefinition> checks,
        String unsupportedReason) {

    public VerificationPlan {
        if (buildSystem == null) {
            throw new IllegalArgumentException("buildSystem must not be null");
        }
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot must not be null");
        }
        workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        checks = checks == null ? List.of() : List.copyOf(checks);
        unsupportedReason = unsupportedReason == null ? "" : unsupportedReason;
        if (!checks.isEmpty() && !unsupportedReason.isBlank()) {
            throw new IllegalArgumentException("supported plans must not have an unsupported reason");
        }
    }

    public boolean supported() {
        return !checks.isEmpty();
    }

    public List<String> requiredCheckIds() {
        return checks.stream().map(VerificationCheckDefinition::checkId).toList();
    }
}
