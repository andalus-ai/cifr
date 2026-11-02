package tech.kayys.cifr.engine.process;

import io.smallrye.mutiny.Uni;

public interface ProcessTreeManager {

    Uni<Void> terminateTree(ProcessId root);
}
