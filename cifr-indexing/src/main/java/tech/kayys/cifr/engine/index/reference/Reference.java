package tech.kayys.cifr.engine.index.reference;

import tech.kayys.cifr.engine.index.symbol.SourceRange;
import tech.kayys.cifr.engine.index.symbol.SymbolId;

import java.nio.file.Path;

public record Reference(
    SymbolId target,
    Path sourceFile,
    SourceRange sourceRange,
    ReferenceKind kind
) {}
