package tech.kayys.cifr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface ReferenceIndex {
    
    Uni<List<Reference>> getReferences(ReferenceRequest request);

}
