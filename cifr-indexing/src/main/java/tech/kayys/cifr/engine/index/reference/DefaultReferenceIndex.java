package tech.kayys.cifr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileIndex;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.symbol.SymbolIndex;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;

import java.util.List;

@ApplicationScoped
public class DefaultReferenceIndex implements ReferenceIndex {
    @Inject ReferenceResolver referenceResolver;
    @Inject FileIndex fileIndex;
    @Inject SymbolIndex symbolIndex;

    @Override
    public Uni<List<Reference>> getReferences(ReferenceRequest request) {
        if (request == null || request.targetSymbol() == null || fileIndex == null || symbolIndex == null) {
            return Uni.createFrom().item(List.of());
        }
        String workspaceId = request.workspaceId();
        return fileIndex.getCurrentIndex(workspaceId)
                .onItem().transformToUni(records -> findDefinition(records, 0, request.targetSymbol(), workspaceId))
                .onItem().transformToUni(definition -> {
                    if (definition == null || referenceResolver == null
                            || !referenceResolver.supports(definition.record())) {
                        return Uni.createFrom().item(List.of());
                    }
                    return referenceResolver.resolveReferences(definition.record(), request.targetSymbol())
                            .onItem().transform(references -> {
                                if (references == null || request.limit() <= 0 || references.size() <= request.limit()) {
                                    return references == null ? List.of() : references;
                                }
                                return references.subList(0, request.limit());
                            });
                });
    }

    /**
     * Populate the IDE-backed per-file symbol cache while searching. This avoids
     * requiring callers to have issued a separate find-symbol operation first.
     */
    private Uni<Definition> findDefinition(List<FileRecord> records, int offset,
                                          tech.kayys.cifr.engine.index.symbol.SymbolId target,
                                          String workspaceId) {
        if (offset >= records.size()) return Uni.createFrom().nullItem();
        FileRecord record = records.get(offset);
        return symbolIndex.getSymbols(record.path(), workspaceId).onItem().transformToUni(symbols -> {
            SymbolInfo match = symbols == null ? null : symbols.stream()
                    .filter(symbol -> symbol.id() != null
                            && (symbol.id().qualifiedName().equalsIgnoreCase(target.qualifiedName())
                            || symbol.name().equalsIgnoreCase(target.qualifiedName())))
                    .findFirst().orElse(null);
            return match != null
                    ? Uni.createFrom().item(new Definition(record, match))
                    : findDefinition(records, offset + 1, target, workspaceId);
        });
    }

    private record Definition(FileRecord record, SymbolInfo symbol) { }
}
