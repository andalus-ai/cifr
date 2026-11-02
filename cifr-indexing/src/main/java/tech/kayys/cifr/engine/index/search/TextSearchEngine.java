package tech.kayys.cifr.engine.index.search;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;

@ApplicationScoped
public class TextSearchEngine {

    @Inject
    LuceneTextSearchProvider luceneProvider;

    @Inject
    NativeTextSearchProvider nativeProvider;

    public Uni<Void> initialize(Path workspaceRoot) {
        return luceneProvider.initialize(workspaceRoot)
            .onFailure().recoverWithUni(fail -> {
                // Log failure, but allow startup to continue with native fallback
                return Uni.createFrom().voidItem();
            });
    }

    public Uni<SearchResult> search(SearchRequest request, Path workspaceRoot) {
        return luceneProvider.search(request, workspaceRoot)
            .onFailure().recoverWithUni(fail -> nativeProvider.search(request, workspaceRoot));
    }
}
