package tech.kayys.cifr.engine.index.reference;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.FileIndex;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DefaultReferenceIndex implements ReferenceIndex {

    @Inject
    ReferenceResolver referenceResolver;
    
    @Inject
    FileIndex fileIndex;

    @Override
    public Uni<List<Reference>> getReferences(ReferenceRequest request) {
        // A real implementation would lookup the FileRecord from FileIndex, 
        // and loop through workspace files to find references.
        // Mocking resolution for now.
        FileRecord mockRecord = new FileRecord(java.nio.file.Path.of("test.java"), "mockHash", 0, "agnostic", false, false, null);
        if (referenceResolver.supports(mockRecord)) {
            return referenceResolver.resolveReferences(mockRecord, request.targetSymbol());
        }
        return Uni.createFrom().item(new ArrayList<>());
    }
}
