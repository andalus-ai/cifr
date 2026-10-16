package tech.kayys.aljabr.engine.index.search;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.index.FileIndex;
import tech.kayys.aljabr.engine.index.FileRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PathSearchEngine {

    public Uni<SearchResult> search(SearchRequest request, FileIndex fileIndex, String workspaceId) {
        return fileIndex.getCurrentIndex(workspaceId).map(records -> {
            List<SearchMatch> matches = records.stream()
                .filter(record -> matchesScope(record, request.scope()))
                .filter(record -> record.path().toString().toLowerCase().contains(request.query().toLowerCase()))
                .limit(request.limit())
                .map(record -> new SearchMatch(record.path(), 0, "", 1.0))
                .collect(Collectors.toList());
                
            boolean truncated = matches.size() >= request.limit();
            SearchMetadata meta = new SearchMetadata(matches.size(), truncated, SearchStrategy.PATH);
            return new SearchResult(matches, meta);
        });
    }
    
    private boolean matchesScope(FileRecord record, SearchScope scope) {
        if (scope == null) return true;
        if (scope.allowedRoles() != null && !scope.allowedRoles().isEmpty()) {
            if (!scope.allowedRoles().contains(record.role())) return false;
        }
        if (scope.pathPrefixes() != null && !scope.pathPrefixes().isEmpty()) {
            boolean matchesPrefix = false;
            String pathStr = record.path().toString();
            for (String prefix : scope.pathPrefixes()) {
                if (pathStr.startsWith(prefix)) {
                    matchesPrefix = true;
                    break;
                }
            }
            if (!matchesPrefix) return false;
        }
        return true;
    }
}
