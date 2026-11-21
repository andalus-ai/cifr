package tech.kayys.cifr.engine.index.dependency;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface DependencyIndex {
    Uni<List<Dependency>> getDependencies(DependencyRequest request);
    void registerDependency(Dependency dependency);

    /** Remove dependency edges emitted by a file in the given workspace. */
    default void removeFile(String source, String workspaceId) { }

    void clear();
}
