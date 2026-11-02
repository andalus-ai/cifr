package tech.kayys.cifr.engine.index.dependency;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultDependencyIndex implements DependencyIndex {

    private final List<Dependency> dependencies = new CopyOnWriteArrayList<>();

    @Override
    public Uni<List<Dependency>> getDependencies(DependencyRequest request) {
        if (request == null || request.sourceIdentifier() == null) {
            return Uni.createFrom().item(new ArrayList<>(dependencies));
        }

        String source = request.sourceIdentifier();
        List<Dependency> filtered = dependencies.stream()
            .filter(d -> d.source() != null && d.source().contains(source))
            .collect(Collectors.toList());

        return Uni.createFrom().item(filtered);
    }

    @Override
    public void registerDependency(Dependency dependency) {
        if (dependency != null) {
            dependencies.add(dependency);
        }
    }

    @Override
    public void clear() {
        dependencies.clear();
    }
}
