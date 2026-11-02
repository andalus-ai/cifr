package tech.kayys.cifr.engine.patch.transaction;

import java.nio.file.Path;

public record ResolvedSymbol(
    String symbolId,
    String name,
    Path file,
    int startLine,
    int endLine,
    String contentHash
) {}
