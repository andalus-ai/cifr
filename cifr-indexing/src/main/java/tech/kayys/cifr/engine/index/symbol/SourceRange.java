package tech.kayys.cifr.engine.index.symbol;

public record SourceRange(
    int startLine,
    int endLine,
    int startColumn,
    int endColumn
) {}
