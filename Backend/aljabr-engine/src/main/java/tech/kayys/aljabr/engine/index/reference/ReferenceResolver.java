package tech.kayys.aljabr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.index.FileRecord;
import java.util.List;

public interface ReferenceResolver {
    
    boolean supports(FileRecord record);
    
    Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target);

}
