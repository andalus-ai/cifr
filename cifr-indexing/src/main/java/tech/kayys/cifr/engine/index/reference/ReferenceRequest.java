package tech.kayys.cifr.engine.index.reference;

import tech.kayys.cifr.engine.index.symbol.SymbolId;

/** A reference lookup may be scoped to a specific workspace. */
public record ReferenceRequest(SymbolId targetSymbol, int limit, String workspaceId) {
    public ReferenceRequest(SymbolId targetSymbol, int limit) {
        this(targetSymbol, limit, null);
    }
}
