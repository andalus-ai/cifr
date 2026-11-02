package tech.kayys.cifr.engine.index;

import tech.kayys.cifr.engine.index.reference.ReferenceKind;

/**
 * Query object for reference lookups (callers, callees, usages, implementations).
 */
public record ReferenceQuery(
    String symbolId,
    ReferenceKind kind,
    int limit
) {
    public static ReferenceQuery callers(String symbolId) {
        return new ReferenceQuery(symbolId, ReferenceKind.CALL, 50);
    }

    public static ReferenceQuery usages(String symbolId) {
        return new ReferenceQuery(symbolId, ReferenceKind.USAGE, 50);
    }
}
