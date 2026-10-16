package tech.kayys.aljabr.engine.process;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

public interface ProcessRegistry {

    Uni<Void> register(ProcessRecord process);

    Uni<Optional<ProcessRecord>> find(ProcessId id);

    Uni<List<ProcessRecord>> findByTask(String taskId);

    Uni<Void> markExited(ProcessId id, int exitCode);
}
