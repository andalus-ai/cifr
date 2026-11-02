package tech.kayys.cifr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileIndex;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.symbol.SymbolIndex;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class DefaultReferenceIndex implements ReferenceIndex {
    @Inject ReferenceResolver referenceResolver;
    @Inject FileIndex fileIndex;
    @Inject SymbolIndex symbolIndex;

    @Override
    public Uni<List<Reference>> getReferences(ReferenceRequest request) {
        if (request == null || request.targetSymbol() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("targetSymbol is required"));
        }
        if (request.limit() <= 0) return Uni.createFrom().item(List.of());

        return symbolIndex.getSymbol(request.targetSymbol(), request.workspaceId())
                .onItem().transformToUni(symbol -> resolveForSymbol(request, symbol));
    }

    private Uni<List<Reference>> resolveForSymbol(ReferenceRequest request, SymbolInfo symbol) {
        if (symbol == null || symbol.file() == null || symbol.workspaceId() == null || symbol.workspaceId().isBlank()) {
            return Uni.createFrom().item(List.of());
        }
        return fileIndex.getCurrentIndex(symbol.workspaceId()).onItem().transformToUni(records -> {
            FileRecord record = records.stream()
                    .filter(candidate -> Objects.equals(candidate.workspaceId(), symbol.workspaceId()))
                    .filter(candidate -> candidate.path() != null && candidate.path().normalize().equals(symbol.file().normalize()))
                    .findFirst().orElse(null);
            if (record == null || !referenceResolver.supports(record)) return Uni.createFrom().item(List.of());
            return referenceResolver.resolveReferences(record, request.targetSymbol(), request.limit());
        });
    }
}
