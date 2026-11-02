package tech.kayys.cifr.engine.index.symbol;

import java.nio.file.Path;
import java.util.Optional;

/** Symbol metadata retains the workspace identity that owns its relative file path. */
public record SymbolInfo(
    SymbolId id,
    String name,
    SymbolKind kind,
    Path file,
    SourceRange range,
    Optional<SymbolId> parent,
    String workspaceId
) {
    public SymbolInfo {
        workspaceId = workspaceId == null || workspaceId.isBlank() ? "default-workspace" : workspaceId;
    }

    /** Compatibility constructor for existing callers. */
    public SymbolInfo(SymbolId id, String name, SymbolKind kind, Path file,
                      SourceRange range, Optional<SymbolId> parent) {
        this(id, name, kind, file, range, parent, "default-workspace");
    }
}
