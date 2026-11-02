package tech.kayys.cifr.engine.index.impact;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.repo.ChangeSet;

public interface ImpactAnalyzer {

    Uni<ImpactReport> analyze(ChangeSet changeSet);
}
