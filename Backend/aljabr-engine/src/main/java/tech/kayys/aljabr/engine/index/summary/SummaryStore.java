package tech.kayys.aljabr.engine.index.summary;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.Optional;

public interface SummaryStore {

    Uni<Optional<FileSummary>> get(Path file);

    Uni<Void> put(FileSummary summary);

    Uni<Void> invalidate(Path file);
}
