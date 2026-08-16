package tech.kayys.aljabr.engine.index.impact;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.repo.ChangeSet;

public interface ImpactAnalyzer {

    Uni<ImpactReport> analyze(ChangeSet changeSet);
}
