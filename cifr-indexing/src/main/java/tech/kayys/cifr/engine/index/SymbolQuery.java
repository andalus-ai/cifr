package tech.kayys.cifr.engine.index;

import tech.kayys.cifr.engine.index.symbol.SymbolKind;

/**
 * Query object for symbol lookups. Replaces wide method signatures with an extensible record.
 */
public record SymbolQuery(
    String name,
    SymbolKind kind,
    String fileScope,
    int limit
) {
    public static SymbolQuery byName(String name) {
        return new SymbolQuery(name, null, null, 20);
    }

    public static SymbolQuery byNameAndKind(String name, SymbolKind kind) {
        return new SymbolQuery(name, kind, null, 20);
    }
}
