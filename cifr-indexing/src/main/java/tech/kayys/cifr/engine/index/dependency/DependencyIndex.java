package tech.kayys.cifr.engine.index.dependency;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface DependencyIndex {
    
    Uni<List<Dependency>> getDependencies(DependencyRequest request);

    void registerDependency(Dependency dependency);

    void clear();
}
