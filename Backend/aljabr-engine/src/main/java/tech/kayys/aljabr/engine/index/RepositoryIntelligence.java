package tech.kayys.aljabr.engine.index;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.index.search.SearchRequest;
import tech.kayys.aljabr.engine.index.search.SearchResult;
import java.nio.file.Path;

public interface RepositoryIntelligence {

    Uni<Void> initializeWorkspace(Path workspaceRoot);
    
    Uni<WorkspaceFingerprint> snapshot(Path workspaceRoot);

    Uni<SearchResult> search(SearchRequest request, Path workspaceRoot, String workspaceId);

    Uni<tech.kayys.aljabr.engine.index.symbol.SymbolInfo> symbol(tech.kayys.aljabr.engine.index.symbol.SymbolId id);

    // Future milestones will add:
    Uni<java.util.List<tech.kayys.aljabr.engine.index.reference.Reference>> references(tech.kayys.aljabr.engine.index.reference.ReferenceRequest request);
    Uni<java.util.List<tech.kayys.aljabr.engine.index.dependency.Dependency>> dependencies(tech.kayys.aljabr.engine.index.dependency.DependencyRequest request);
    Uni<java.util.List<tech.kayys.aljabr.engine.index.relation.RelatedFile>> related(tech.kayys.aljabr.engine.index.relation.RelatedFileRequest request);
}
