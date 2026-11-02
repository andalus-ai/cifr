package tech.kayys.cifr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.repo.GitRepositoryService;
import tech.kayys.cifr.engine.repo.RepositorySnapshot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DefaultWorkspaceGuard implements WorkspaceGuard {

    @Inject
    GitRepositoryService gitService;

    @Override
    public WorkspaceCheck check(RepositorySnapshot baseline, Set<Path> affectedPaths) {
        Path root = Paths.get(System.getProperty("user.dir"));
        RepositorySnapshot current = gitService != null ? gitService.captureSnapshot(root) : null;

        if (baseline == null || current == null) {
            return new WorkspaceCheck(true, List.of());
        }

        List<String> conflicts = new ArrayList<>();

        for (Path affected : affectedPaths) {
            Path normalized = affected.isAbsolute() ? root.relativize(affected).normalize() : affected.normalize();
            String relativeStr = normalized.toString();

            if (current.changedFiles() != null && current.changedFiles().contains(relativeStr)) {
                if (baseline.changedFiles() == null || !baseline.changedFiles().contains(relativeStr)) {
                    conflicts.add(relativeStr + " (uncommitted external modifications)");
                }
            }

            if (!baseline.commitHash().equals(current.commitHash()) && !"unknown".equals(baseline.commitHash())) {
                if (current.changedFiles() != null && current.changedFiles().contains(relativeStr)) {
                    conflicts.add(relativeStr + " (branch/commit diverged: " + baseline.commitHash() + " -> " + current.commitHash() + ")");
                }
            }
        }

        if (conflicts.isEmpty()) {
            return new WorkspaceCheck(true, List.of());
        } else {
            return new WorkspaceCheck(false, conflicts);
        }
    }
}
