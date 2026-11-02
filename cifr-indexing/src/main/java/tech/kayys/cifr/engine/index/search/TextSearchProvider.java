package tech.kayys.cifr.engine.index.search;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

public interface TextSearchProvider {
    
    /**
     * Optional initialization method if the provider needs to build an index.
     */
    Uni<Void> initialize(Path workspaceRoot);

    /**
     * Executes the text search strategy.
     */
    Uni<SearchResult> search(SearchRequest request, Path workspaceRoot);
}
