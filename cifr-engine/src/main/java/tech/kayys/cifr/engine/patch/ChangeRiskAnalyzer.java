package tech.kayys.cifr.engine.patch;

import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.repo.ChangeSet;

/**
 * Computes mutation risk from the concrete change set and repository impact.
 *
 * <p>The model may propose a change, but this component is runtime-owned and
 * deterministic. It is therefore suitable for authorization and verification
 * decisions.</p>
 */
public interface ChangeRiskAnalyzer {
    ChangeRisk analyze(ChangeSet changes, ImpactReport impact);
}
