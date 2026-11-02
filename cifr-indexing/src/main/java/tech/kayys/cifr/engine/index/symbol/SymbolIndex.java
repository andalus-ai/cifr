package tech.kayys.cifr.engine.index.symbol;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;

public interface SymbolIndex {
    Uni<List<SymbolInfo>> getSymbols(Path file);

    /** Workspace-aware lookup; legacy implementations retain their old behavior. */
    default Uni<List<SymbolInfo>> getSymbols(Path file, String workspaceId) {
        return getSymbols(file);
    }

    Uni<List<SymbolInfo>> searchSymbols(String query);

    default Uni<List<SymbolInfo>> searchSymbols(String query, String workspaceId) {
        return searchSymbols(query);
    }

    Uni<SymbolInfo> getSymbol(SymbolId id);\n\n    default Uni<SymbolInfo> getSymbol(SymbolId id, String workspaceId) {\n        return getSymbol(id);\n    }
    void registerSymbol(SymbolInfo info);
    void clear();

    default void clearWorkspace(String workspaceId) {
        clear();
    }
}
