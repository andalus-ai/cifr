package tech.kayys.cifr.engine.index.symbol;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;

public interface SymbolIndex {
    
    Uni<List<SymbolInfo>> getSymbols(Path file);
    
    Uni<List<SymbolInfo>> searchSymbols(String query);
    
    Uni<SymbolInfo> getSymbol(SymbolId id);

    void registerSymbol(SymbolInfo info);

    void clear();
}
