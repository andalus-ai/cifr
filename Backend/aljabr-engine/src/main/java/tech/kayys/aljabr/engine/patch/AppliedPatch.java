package tech.kayys.aljabr.engine.patch;

import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;

public record AppliedPatch(
    String patchId,
    ChangeSet changes,
    RepositorySnapshot resultingSnapshot
) {}
