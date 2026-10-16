package tech.kayys.aljabr.engine.index.search;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class NativeTextSearchProvider implements TextSearchProvider {

    @Override
    public Uni<Void> initialize(Path workspaceRoot) {
        // Native search does not require upfront indexing
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<SearchResult> search(SearchRequest request, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            List<SearchMatch> matches = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(workspaceRoot)) {
                paths.filter(Files::isRegularFile)
                     .filter(this::shouldInclude)
                     .forEach(path -> {
                         if (matches.size() >= request.limit()) return;
                         searchFile(path, request.query(), matches, request.limit());
                     });
            } catch (IOException e) {
                throw new RuntimeException("Native text search failed", e);
            }
            
            boolean truncated = matches.size() >= request.limit();
            SearchMetadata meta = new SearchMetadata(matches.size(), truncated, SearchStrategy.TEXT);
            return new SearchResult(matches, meta);
        });
    }

    private void searchFile(Path path, String query, List<SearchMatch> matches, int limit) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                if (matches.size() >= limit) break;
                String line = lines.get(i);
                if (line.contains(query)) {
                    matches.add(new SearchMatch(path, i + 1, line.trim(), 1.0));
                }
            }
        } catch (IOException e) {
            // Ignore unreadable files
        }
    }

    private boolean shouldInclude(Path path) {
        String pStr = path.toString();
        return !pStr.contains("/.git/") && !pStr.contains("/.idea/") && !pStr.contains("/target/") && !pStr.contains("/node_modules/");
    }
}
