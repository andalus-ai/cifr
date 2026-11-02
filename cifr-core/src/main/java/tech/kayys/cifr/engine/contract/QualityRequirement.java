package tech.kayys.cifr.engine.contract;

/**
 * Quality requirement metrics enforced on output artifacts (coverage, style, lint score).
 */
public record QualityRequirement(
    double minimumCoveragePercent,
    boolean enforceZeroLintWarnings,
    boolean requireAuditLog
) {
    public static QualityRequirement standard() {
        return new QualityRequirement(80.0, true, true);
    }

    public static QualityRequirement relaxed() {
        return new QualityRequirement(0.0, false, false);
    }
}
