package tech.kayys.cifr.engine.index.symbol;

import java.nio.file.Path;
import java.util.Optional;

public record SymbolInfo(
    SymbolId id,
    String name,
    SymbolKind kind,
    Path file,
    SourceRange range,
    Optional<SymbolId> parent
) {}
