package tech.kayys.cifr.engine.index.symbol;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.index.FileRecord;
import java.util.List;

public interface SymbolResolver {
    
    boolean supports(FileRecord record);
    
    Uni<List<SymbolInfo>> resolve(FileRecord record);
}
