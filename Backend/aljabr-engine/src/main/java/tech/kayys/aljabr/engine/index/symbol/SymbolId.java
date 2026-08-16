package tech.kayys.aljabr.engine.index.symbol;

public record SymbolId(
    String qualifiedName,
    String signature,
    String contentHash
) {}
