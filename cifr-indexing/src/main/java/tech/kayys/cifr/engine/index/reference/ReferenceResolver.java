package tech.kayys.cifr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.index.symbol.SymbolId;
import tech.kayys.cifr.engine.index.FileRecord;
import java.util.List;

public interface ReferenceResolver {
    boolean supports(FileRecord record);
    Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target);

    default Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target, int limit) {
        return resolveReferences(record, target);
    }
}
