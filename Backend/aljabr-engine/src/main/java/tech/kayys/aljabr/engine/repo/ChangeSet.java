package tech.kayys.aljabr.engine.repo;

import java.util.List;

public record ChangeSet(
    List<ChangeRecord> changes
) {
    public ChangeSet {
        changes = List.copyOf(changes);
    }
}
