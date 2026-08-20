package tech.kayys.aljabr.engine.process;

import io.smallrye.mutiny.Uni;

public interface ProcessTreeManager {

    Uni<Void> terminateTree(ProcessId root);
}
