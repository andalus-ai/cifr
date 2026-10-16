package tech.kayys.aljabr.engine.repo;

import java.nio.file.Path;
import java.util.List;

public record FileChange(
    Path path,
    ChangeType type,
    String oldHash,
    String newHash,
    List<String> changedRanges
) {
    public FileChange {
        changedRanges = changedRanges != null ? List.copyOf(changedRanges) : List.of();
    }
}
