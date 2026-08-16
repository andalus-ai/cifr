package tech.kayys.aljabr.engine.patch;

import tech.kayys.aljabr.engine.index.impact.ImpactReport;
import tech.kayys.aljabr.engine.repo.ChangeSet;

public interface VerificationPlanner {

    VerificationPlan plan(ChangeSet changes, ImpactReport impact);
}
