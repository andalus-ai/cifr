package tech.kayys.cifr.engine.patch;

import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.index.impact.ImpactConfidence;
import tech.kayys.cifr.engine.index.impact.ImpactNode;
import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.ChangeType;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultChangeRiskAnalyzerTest {

    private final DefaultChangeRiskAnalyzer analyzer = new DefaultChangeRiskAnalyzer();

    @Test
    void sensitivePathIsCritical() {
        ChangeSet changes = new ChangeSet(List.of(
                new ChangeRecord(".env", ChangeType.MODIFIED, "a", "b", "p", "s")));

        ChangeRisk risk = analyzer.analyze(changes, emptyImpact());

        assertEquals(RiskLevel.CRITICAL, risk.level());
    }

    @Test
    void smallLocalizedChangeIsLow() {
        ChangeSet changes = new ChangeSet(List.of(
                new ChangeRecord("src/Foo.java", ChangeType.MODIFIED, "a", "b", "p", "s")));

        ChangeRisk risk = analyzer.analyze(changes,
                new ImpactReport(List.of(), List.of(), List.of(), ImpactConfidence.CERTAIN));

        assertEquals(RiskLevel.LOW, risk.level());
    }

    @Test
    void highFanoutBecomesHighRisk() {
        List<ImpactNode> files = java.util.stream.IntStream.range(0, 16)
                .mapToObj(i -> new ImpactNode(
                        "File" + i, "FILE", Path.of("src/F" + i + ".java"),
                        0.75, List.of("reference")))
                .toList();

        ChangeSet changes = new ChangeSet(List.of(
                new ChangeRecord("src/Foo.java", ChangeType.MODIFIED, "a", "b", "p", "s")));

        ChangeRisk risk = analyzer.analyze(
                changes,
                new ImpactReport(List.of(), files, List.of(), ImpactConfidence.HIGH));

        assertEquals(RiskLevel.HIGH, risk.level());
    }

    private ImpactReport emptyImpact() {
        return new ImpactReport(List.of(), List.of(), List.of(), ImpactConfidence.CERTAIN);
    }
}
