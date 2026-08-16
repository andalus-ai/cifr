package tech.kayys.aljabr.engine.index.reference;

import tech.kayys.aljabr.engine.index.symbol.SourceRange;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;

import java.nio.file.Path;

public record Reference(
    SymbolId target,
    Path sourceFile,
    SourceRange sourceRange,
    ReferenceKind kind
) {}
