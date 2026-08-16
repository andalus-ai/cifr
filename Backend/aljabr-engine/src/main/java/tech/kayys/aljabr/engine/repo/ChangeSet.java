package tech.kayys.aljabr.engine.repo;

import java.util.List;

public record ChangeSet(
    List<ChangeRecord> changes,
    List<FileChange> fileChanges,
    List<SymbolChange> symbolChanges
) {
    public ChangeSet(List<ChangeRecord> changes) {
        this(changes, List.of(), List.of());
    }

    public ChangeSet {
        changes = changes != null ? List.copyOf(changes) : List.of();
        fileChanges = fileChanges != null ? List.copyOf(fileChanges) : List.of();
        symbolChanges = symbolChanges != null ? List.copyOf(symbolChanges) : List.of();
    }
}
