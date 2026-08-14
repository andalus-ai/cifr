package tech.kayys.aljabr.engine.index;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.index.search.SearchRequest;
import tech.kayys.aljabr.engine.index.search.SearchResult;
import java.nio.file.Path;

public interface RepositoryIntelligence {

    Uni<Void> initializeWorkspace(Path workspaceRoot);
    
    Uni<WorkspaceFingerprint> snapshot(Path workspaceRoot);

    Uni<SearchResult> search(SearchRequest request, Path workspaceRoot, String workspaceId);

    // Future milestones will add:
    // Uni<SymbolInfo> symbol(SymbolRequest request);
    // Uni<List<Reference>> references(ReferenceRequest request);
    // Uni<List<Dependency>> dependencies(DependencyRequest request);
    // Uni<List<RelatedFile>> related(RelatedFileRequest request);
}
