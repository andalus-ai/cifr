package tech.kayys.aljabr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public interface WorkspaceOverlay {

    Optional<String> read(Path file);

    Uni<Void> stage(Path file, String content);

    Uni<Void> commit();

    Uni<Void> rollback();

    Map<Path, String> stagedChanges();
}
