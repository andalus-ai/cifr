package tech.kayys.cifr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.verification.VerificationLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts change risk and repository impact into a deterministic verification
 * plan. The planner never claims that verification passed; it only selects
 * evidence that must be collected.
 */
@ApplicationScoped
public class DefaultVerificationPlanner implements VerificationPlanner {

    @Override
    public VerificationPlan plan(ChangeSet changes, ImpactReport impact) {
        ChangeRisk risk = inferRisk(changes, impact);
        List<VerificationAction> actions = new ArrayList<>();

        actions.add(new VerificationAction("FORMAT_OR_LINT", "changed-files", List.of()));
        actions.add(new VerificationAction("COMPILE", "affected-module", List.of()));

        if (impact != null && !impact.likelyTests().isEmpty()) {
            actions.add(new VerificationAction(
                    "TEST", "impacted-tests",
                    impact.likelyTests().stream().map(java.nio.file.Path::toString).toList()));
        } else {
            actions.add(new VerificationAction("TEST", "changed-area", List.of()));
        }

        if (risk.level().ordinal() >= RiskLevel.HIGH.ordinal()) {
            actions.add(new VerificationAction("FULL_TEST_SUITE", "repository", List.of()));
            actions.add(new VerificationAction("SECURITY_REVIEW", "changed-surface", List.of()));
        }

        if (risk.level() == RiskLevel.CRITICAL) {
            actions.add(new VerificationAction("HUMAN_REVIEW", "change", risk.reasons()));
        }

        VerificationLevel level = switch (risk.level()) {
            case LOW -> VerificationLevel.TARGETED_TESTS_L2;
            case MEDIUM -> VerificationLevel.AFFECTED_TESTS_L3;
            case HIGH -> VerificationLevel.MODULE_TESTS_L4;
            case CRITICAL -> VerificationLevel.FULL_SUITE_L5;
        };

        return new VerificationPlan(
                actions,
                level,
                "Risk=" + risk.level() + "; " + String.join("; ", risk.reasons()));
    }

    private ChangeRisk inferRisk(ChangeSet changes, ImpactReport impact) {
        int files = changes == null ? 0 : changes.changes().size();
        int impacted = impact == null ? 0 : impact.affectedFiles().size();

        if (files > 10 || impacted > 15) {
            return new ChangeRisk(RiskLevel.HIGH, List.of("Large change/impact surface"));
        }
        if (files > 3 || impacted > 5) {
            return new ChangeRisk(RiskLevel.MEDIUM, List.of("Moderate change/impact surface"));
        }

        if (changes != null) {
            for (ChangeRecord c : changes.changes()) {
                String p = c.path() == null ? "" : c.path().toLowerCase();
                if (p.contains("migration") || p.endsWith(".sql") || p.contains("/proto/")
                        || p.contains("/api/") || p.endsWith("pom.xml")) {
                    return new ChangeRisk(RiskLevel.HIGH,
                            List.of("Contract/build/schema-sensitive file changed: " + c.path()));
                }
            }
        }
        return new ChangeRisk(RiskLevel.LOW, List.of("Small localized change"));
    }
}
