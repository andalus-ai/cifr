package tech.kayys.aljabr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultWorkspaceGuard implements WorkspaceGuard {

    @Inject
    GitRepositoryService gitService;

    @Override
    public WorkspaceCheck check(RepositorySnapshot baseline, Set<Path> affectedPaths) {
        RepositorySnapshot current = gitService.captureSnapshot(Paths.get(System.getProperty("user.dir")));

        if (baseline == null) {
            return new WorkspaceCheck(true, List.of());
        }

        if (baseline.commitHash().equals(current.commitHash())) {
            return new WorkspaceCheck(true, List.of());
        }

        List<String> conflicts = affectedPaths.stream()
                .map(Path::toString)
                .filter(p -> current.changedFiles().contains(p))
                .collect(Collectors.toList());

        if (conflicts.isEmpty()) {
            return new WorkspaceCheck(true, List.of());
        } else {
            return new WorkspaceCheck(false, conflicts);
        }
    }
}
