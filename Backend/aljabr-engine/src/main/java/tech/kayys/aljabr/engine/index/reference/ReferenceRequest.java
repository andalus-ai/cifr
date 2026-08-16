package tech.kayys.aljabr.engine.index.reference;

import tech.kayys.aljabr.engine.index.symbol.SymbolId;

public record ReferenceRequest(
    SymbolId targetSymbol,
    int limit
) {}
