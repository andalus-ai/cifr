package tech.kayys.cifr.engine.patch;

import tech.kayys.cifr.engine.repo.RepositorySnapshot;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;

import java.nio.file.Path;
import java.util.Set;

public interface WorkspaceGuard {
    WorkspaceCheck check(RepositorySnapshot baseline, Set<Path> affectedPaths, ToolExecutionContext context);
}
