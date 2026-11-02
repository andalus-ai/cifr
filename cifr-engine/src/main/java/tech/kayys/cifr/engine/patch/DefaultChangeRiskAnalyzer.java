package tech.kayys.cifr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.index.impact.ImpactConfidence;
import tech.kayys.cifr.engine.index.impact.ImpactNode;
import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.ChangeType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conservative deterministic risk model for workspace mutations.
 *
 * <p>The score is intentionally explainable. It combines change volume,
 * destructive operations, sensitive paths, public-surface indicators,
 * repository impact and impact confidence. A critical signal is never
 * downgraded by a low aggregate score.</p>
 */
@ApplicationScoped
public class DefaultChangeRiskAnalyzer implements ChangeRiskAnalyzer {

    @Override
    public ChangeRisk analyze(ChangeSet changes, ImpactReport impact) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (changes == null || changes.changes().isEmpty()) {
            return new ChangeRisk(RiskLevel.LOW, List.of("No file mutations detected"));
        }

        int files = changes.changes().size();
        score += Math.min(files * 4, 20);
        if (files > 10) reasons.add("More than 10 files changed");
        else if (files > 5) reasons.add("Multiple files changed: " + files);

        for (ChangeRecord change : changes.changes()) {
            String path = normalize(change.path());
            ChangeType type = change.type();

            if (type == ChangeType.DELETED) {
                score += 25;
                reasons.add("File deletion: " + path);
            }

            if (isSensitivePath(path)) {
                score += 35;
                reasons.add("Sensitive configuration/security path: " + path);
            }

            if (isBuildOrDeploymentPath(path)) {
                score += 20;
                reasons.add("Build/deployment configuration changed: " + path);
            }

            if (looksLikeApiSurface(path)) {
                score += 15;
                reasons.add("API/public contract surface changed: " + path);
            }

            if (looksLikeSchema(path)) {
                score += 25;
                reasons.add("Database/schema contract changed: " + path);
            }

            if (looksLikeInfrastructure(path)) {
                score += 25;
                reasons.add("Infrastructure/deployment surface changed: " + path);
            }
        }

        if (changes.symbolChanges() != null && !changes.symbolChanges().isEmpty()) {
            score += Math.min(changes.symbolChanges().size() * 3, 18);
            reasons.add("Symbol-level changes detected: " + changes.symbolChanges().size());
        }

        if (impact != null) {
            int affectedSymbols = impact.affectedSymbols().size();
            int affectedFiles = impact.affectedFiles().size();
            int tests = impact.likelyTests().size();

            score += Math.min(affectedSymbols * 2, 20);
            score += Math.min(affectedFiles, 20);

            if (affectedSymbols >= 15) {
                score += 20;
                reasons.add("High symbol fan-out: " + affectedSymbols);
            } else if (affectedSymbols >= 10) {
                score += 10;
                reasons.add("High symbol fan-out: " + affectedSymbols);
            }

            if (affectedFiles >= 15) {
                score += 20;
                reasons.add("High file fan-out: " + affectedFiles);
            } else if (affectedFiles >= 10) {
                score += 10;
                reasons.add("High file fan-out: " + affectedFiles);
            }

            if (tests == 0 && affectedSymbols > 0) {
                score += 15;
                reasons.add("No likely tests identified for impacted symbols");
            }

            if (impact.confidence() == ImpactConfidence.LOW) {
                score += 15;
                reasons.add("Impact analysis confidence is LOW");
            } else if (impact.confidence() == ImpactConfidence.MEDIUM) {
                score += 8;
                reasons.add("Impact analysis confidence is MEDIUM");
            }

            double maxImpact = impact.affectedFiles().stream()
                    .mapToDouble(ImpactNode::impactScore)
                    .max()
                    .orElse(0.0);
            if (maxImpact >= 0.90) {
                score += 20;
                reasons.add("At least one affected file has critical impact score");
            } else if (maxImpact >= 0.70) {
                score += 10;
                reasons.add("At least one affected file has high impact score");
            }
        } else {
            score += 15;
            reasons.add("Impact analysis unavailable; conservative risk uplift applied");
        }

        RiskLevel level;
        if (hasCriticalSignal(changes)) {
            level = RiskLevel.CRITICAL;
        } else if (score >= 80) {
            level = RiskLevel.CRITICAL;
        } else if (score >= 50) {
            level = RiskLevel.HIGH;
        } else if (score >= 20) {
            level = RiskLevel.MEDIUM;
        } else {
            level = RiskLevel.LOW;
        }

        if (reasons.isEmpty()) {
            reasons.add("No elevated risk indicators detected");
        }
        return new ChangeRisk(level, List.copyOf(reasons));
    }

    private boolean hasCriticalSignal(ChangeSet changes) {
        return changes.changes().stream().anyMatch(c -> {
            String path = normalize(c.path());
            return isSensitivePath(path)
                    || (c.type() == ChangeType.DELETED && isBuildOrDeploymentPath(path));
        });
    }

    private boolean isSensitivePath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.equals(".env")
                || p.endsWith("/.env")
                || p.contains("/secrets/")
                || p.contains("/.ssh/")
                || p.contains("credentials")
                || p.contains("id_rsa")
                || p.contains("id_ed25519");
    }

    private boolean isBuildOrDeploymentPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith("pom.xml")
                || p.endsWith("build.gradle")
                || p.endsWith("build.gradle.kts")
                || p.contains("/.github/workflows/")
                || p.contains("/kubernetes/")
                || p.contains("/helm/")
                || p.endsWith("dockerfile")
                || p.endsWith(".tf");
    }

    private boolean looksLikeApiSurface(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("/api/")
                || p.contains("/proto/")
                || p.endsWith(".proto")
                || p.contains("openapi")
                || p.contains("swagger");
    }

    private boolean looksLikeSchema(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("/migration")
                || p.contains("/migrations/")
                || p.endsWith(".sql")
                || p.contains("schema");
    }

    private boolean looksLikeInfrastructure(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("terraform")
                || p.contains("k8s")
                || p.contains("kubernetes")
                || p.contains("helm")
                || p.endsWith("docker-compose.yml")
                || p.endsWith("docker-compose.yaml");
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) return "";
        return Path.of(path).normalize().toString().replace('\\', '/');
    }
}
