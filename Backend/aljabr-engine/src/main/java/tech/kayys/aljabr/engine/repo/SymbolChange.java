package tech.kayys.aljabr.engine.repo;

import tech.kayys.aljabr.engine.index.symbol.SymbolId;

public record SymbolChange(
    SymbolId symbol,
    ChangeType type,
    String oldSignature,
    String newSignature
) {}
