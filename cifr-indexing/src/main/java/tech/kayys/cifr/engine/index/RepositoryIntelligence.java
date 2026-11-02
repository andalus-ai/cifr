package tech.kayys.cifr.engine.index;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.index.search.SearchRequest;
import tech.kayys.cifr.engine.index.search.SearchResult;
import java.nio.file.Path;

public interface RepositoryIntelligence {

    Uni<Void> initializeWorkspace(Path workspaceRoot);
    
    Uni<WorkspaceFingerprint> snapshot(Path workspaceRoot);

    Uni<SearchResult> search(SearchRequest request, Path workspaceRoot, String workspaceId);

    Uni<tech.kayys.cifr.engine.index.symbol.SymbolInfo> symbol(tech.kayys.cifr.engine.index.symbol.SymbolId id);

    // Future milestones will add:
    Uni<java.util.List<tech.kayys.cifr.engine.index.reference.Reference>> references(tech.kayys.cifr.engine.index.reference.ReferenceRequest request);
    Uni<java.util.List<tech.kayys.cifr.engine.index.dependency.Dependency>> dependencies(tech.kayys.cifr.engine.index.dependency.DependencyRequest request);
    Uni<java.util.List<tech.kayys.cifr.engine.index.relation.RelatedFile>> related(tech.kayys.cifr.engine.index.relation.RelatedFileRequest request);
}
