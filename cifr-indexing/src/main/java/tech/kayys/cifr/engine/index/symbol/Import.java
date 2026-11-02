package tech.kayys.cifr.engine.index.symbol;

import java.util.List;

/**
 * Import declaration extracted from a parsed source file.
 */
public record Import(
    String qualifiedName,
    boolean isStatic,
    boolean isWildcard
) {}
