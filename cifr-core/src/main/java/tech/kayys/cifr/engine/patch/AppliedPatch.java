package tech.kayys.cifr.engine.patch;

import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.RepositorySnapshot;

public record AppliedPatch(
    String patchId,
    ChangeSet changes,
    RepositorySnapshot resultingSnapshot
) {}
