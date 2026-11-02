package tech.kayys.cifr.engine.patch;

import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.repo.ChangeSet;

public interface VerificationPlanner {

    VerificationPlan plan(ChangeSet changes, ImpactReport impact);
}
