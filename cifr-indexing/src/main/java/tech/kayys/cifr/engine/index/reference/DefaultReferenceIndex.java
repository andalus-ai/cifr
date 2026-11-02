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
        if (request == null || request.targetSymbol() == null) {
            return Uni.createFrom().item(List.of());
        }
        String workspaceId = request.workspaceId();
        return symbolIndex.getSymbol(request.targetSymbol(), workspaceId)
                .onItem().transformToUni(symbol -> resolveDefinition(symbol, request, workspaceId));
    }

    private Uni<List<Reference>> resolveDefinition(SymbolInfo symbol, ReferenceRequest request, String workspaceId) {
        if (symbol == null || symbol.file() == null || referenceResolver == null || fileIndex == null) {
            return Uni.createFrom().item(List.of());
        }
        return fileIndex.getCurrentIndex(workspaceId).onItem().transformToUni(records -> {
            FileRecord record = records.stream()
                    .filter(candidate -> samePath(candidate.path(), symbol.file()))
                    .findFirst().orElse(null);
            if (record == null || !referenceResolver.supports(record)) {
                return Uni.createFrom().item(List.of());
            }
            return referenceResolver.resolveReferences(record, request.targetSymbol())
                    .onItem().transform(references -> {
                        if (references == null || request.limit() <= 0 || references.size() <= request.limit()) {
                            return references == null ? List.of() : references;
                        }
                        return references.subList(0, request.limit());
                    });
        });
    }

    private static boolean samePath(java.nio.file.Path left, java.nio.file.Path right) {
        if (left == null || right == null) return false;
        java.nio.file.Path a = left.normalize();
        java.nio.file.Path b = right.normalize();
        return a.equals(b) || a.endsWith(b) || b.endsWith(a);
    }
}
