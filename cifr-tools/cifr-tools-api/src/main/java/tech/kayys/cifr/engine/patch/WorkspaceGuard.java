package tech.kayys.cifr.engine.patch;

import tech.kayys.cifr.engine.repo.RepositorySnapshot;

import java.nio.file.Path;
import java.util.Set;

public interface WorkspaceGuard {

    WorkspaceCheck check(RepositorySnapshot baseline, Set<Path> affectedPaths);

}
