package tech.kayys.cifr.engine.index.test;

import io.smallrye.mutiny.Uni;
import java.util.List;
import tech.kayys.cifr.engine.repo.ChangeSet;

public interface TestDiscovery {

    Uni<List<TestCandidate>> findTests(ChangeSet changeSet);
}
