package tech.kayys.aljabr.engine.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.search.PathSearchEngine;
import tech.kayys.aljabr.engine.index.search.SearchRequest;
import tech.kayys.aljabr.engine.index.search.SearchResult;
import tech.kayys.aljabr.engine.index.search.SearchStrategy;
import tech.kayys.aljabr.engine.index.search.TextSearchEngine;

import java.nio.file.Path;

@ApplicationScoped
public class DefaultRepositoryIntelligence implements RepositoryIntelligence {

    @Inject
    FileIndex fileIndex;

    @Inject
    PathSearchEngine pathSearchEngine;

    @Inject
    TextSearchEngine textSearchEngine;

    @Override
    public Uni<Void> initializeWorkspace(Path workspaceRoot) {
        return textSearchEngine.initialize(workspaceRoot);
    }

    @Override
    public Uni<WorkspaceFingerprint> snapshot(Path workspaceRoot) {
        return fileIndex.computeFingerprint(workspaceRoot);
    }

    @Override
    public Uni<SearchResult> search(SearchRequest request, Path workspaceRoot, String workspaceId) {
        if (request.preferredStrategies() != null && request.preferredStrategies().contains(SearchStrategy.PATH)) {
            return pathSearchEngine.search(request, fileIndex, workspaceId);
        }
        
        // Default to text search
        return textSearchEngine.search(request, workspaceRoot);
    }
    
    @Inject
    tech.kayys.aljabr.engine.index.symbol.SymbolIndex symbolIndex;

    @Override
    public Uni<tech.kayys.aljabr.engine.index.symbol.SymbolInfo> symbol(tech.kayys.aljabr.engine.index.symbol.SymbolId id) {
        return symbolIndex.getSymbol(id);
    }
    
    @Inject
    tech.kayys.aljabr.engine.index.reference.ReferenceIndex referenceIndex;

    @Override
    public Uni<java.util.List<tech.kayys.aljabr.engine.index.reference.Reference>> references(tech.kayys.aljabr.engine.index.reference.ReferenceRequest request) {
        return referenceIndex.getReferences(request);
    }

    @Inject
    tech.kayys.aljabr.engine.index.dependency.DependencyIndex dependencyIndex;

    @Inject
    tech.kayys.aljabr.engine.index.relation.RelationIndex relationIndex;

    @Override
    public Uni<java.util.List<tech.kayys.aljabr.engine.index.dependency.Dependency>> dependencies(tech.kayys.aljabr.engine.index.dependency.DependencyRequest request) {
        return dependencyIndex.getDependencies(request);
    }

    @Override
    public Uni<java.util.List<tech.kayys.aljabr.engine.index.relation.RelatedFile>> related(tech.kayys.aljabr.engine.index.relation.RelatedFileRequest request) {
        return relationIndex.getRelatedFiles(request);
    }
}
