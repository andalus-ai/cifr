package tech.kayys.cifr.engine.index.reference;

import tech.kayys.cifr.engine.index.symbol.SymbolId;

public record ReferenceRequest(
    SymbolId targetSymbol,
    int limit
) {}
