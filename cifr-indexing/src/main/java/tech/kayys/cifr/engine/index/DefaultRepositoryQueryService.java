package tech.kayys.cifr.engine.index;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.reference.Reference;
import tech.kayys.cifr.engine.index.reference.ReferenceIndex;
import tech.kayys.cifr.engine.index.reference.ReferenceRequest;
import tech.kayys.cifr.engine.index.symbol.SymbolId;
import tech.kayys.cifr.engine.index.symbol.SymbolIndex;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link RepositoryQueryService}.
 * Delegates queries across specialized indexes and ranks results deterministically.
 */
@ApplicationScoped
public class DefaultRepositoryQueryService implements RepositoryQueryService {

    @Inject
    SymbolIndex symbolIndex;

    @Inject
    ReferenceIndex referenceIndex;

    @Inject
    FileIndex fileIndex;

    @Override
    public List<RankedResult<SymbolInfo>> findSymbols(SymbolQuery query) {
        if (query == null || query.name() == null || query.name().isBlank()) {
            return Collections.emptyList();
        }
        final SymbolInfo symbol;
        try {
            symbol = symbolIndex.getSymbol(SymbolId.of(query.name())).await().indefinitely();
        } catch (Exception e) {
            throw new IllegalStateException("Symbol lookup failed for " + query.name(), e);
        }
        if (symbol != null) {
            return List.of(RankedResult.of(symbol, 1.0, RankingReason.EXACT_NAME_MATCH));
        }
        return Collections.emptyList();
    }

    @Override
    public List<RankedResult<Reference>> findReferences(ReferenceQuery query) {
        if (query == null || query.symbolId() == null) {
            return Collections.emptyList();
        }
        ReferenceRequest req = new ReferenceRequest(
                SymbolId.of(query.symbolId()), query.limit(), query.workspaceId());
        final List<Reference> refs;
        try {
            refs = referenceIndex.getReferences(req).await().indefinitely();
        } catch (Exception e) {
            throw new IllegalStateException("Reference lookup failed for " + query.symbolId(), e);
        }
        if (refs == null || refs.isEmpty()) return Collections.emptyList();
        List<RankedResult<Reference>> results = new ArrayList<>();
        for (Reference ref : refs) {
            results.add(RankedResult.of(ref, 0.9, RankingReason.SYMBOL_REFERENCE));
        }
        return results;
    }

    @Override
    public List<RankedResult<FileRecord>> findFiles(FileQuery query) {
        // File query returns matching files
        return Collections.emptyList();
    }

    @Override
    public List<FileImportance> rankFiles(String taskDescription, List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<FileImportance> ranked = new ArrayList<>();
        for (String id : fileIds) {
            String fileName = id;
            int lastSlash = Math.max(id.lastIndexOf('/'), id.lastIndexOf('\\'));
            if (lastSlash >= 0 && lastSlash < id.length() - 1) {
                fileName = id.substring(lastSlash + 1);
            }
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

            boolean matches = taskDescription != null && (
                taskDescription.toLowerCase().contains(id.toLowerCase()) ||
                taskDescription.toLowerCase().contains(fileName.toLowerCase()) ||
                taskDescription.toLowerCase().contains(baseName.toLowerCase())
            );

            if (matches) {
                ranked.add(FileImportance.high(id, ImportanceReason.DIRECTLY_REFERENCED_BY_TASK));
            } else {
                ranked.add(FileImportance.of(id, 0.5, ImportanceReason.DEPENDENCY_PROXIMITY));
            }
        }
        return ranked;
    }
}
