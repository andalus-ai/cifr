package tech.kayys.cifr.engine.index.reference;

import tech.kayys.cifr.engine.index.symbol.SymbolId;

/** Workspace-scoped request to find usages of a symbol. */
public record ReferenceRequest(
    SymbolId targetSymbol,
    int limit,
    String workspaceId
) {
    public ReferenceRequest {
        workspaceId = workspaceId == null || workspaceId.isBlank() ? "default-workspace" : workspaceId;
    }

    public ReferenceRequest(SymbolId targetSymbol, int limit) {
        this(targetSymbol, limit, "default-workspace");
    }
}
