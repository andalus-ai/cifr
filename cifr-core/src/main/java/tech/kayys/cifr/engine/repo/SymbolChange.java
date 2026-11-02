package tech.kayys.cifr.engine.repo;

import tech.kayys.cifr.engine.index.symbol.SymbolId;

public record SymbolChange(
    SymbolId symbol,
    ChangeType type,
    String oldSignature,
    String newSignature
) {}
