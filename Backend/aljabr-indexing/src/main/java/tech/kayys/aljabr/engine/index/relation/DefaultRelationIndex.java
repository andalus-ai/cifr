package tech.kayys.aljabr.engine.index.relation;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.reference.ReferenceIndex;
import tech.kayys.aljabr.engine.index.dependency.DependencyIndex;
import tech.kayys.aljabr.engine.index.FileIndex;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DefaultRelationIndex implements RelationIndex {

    @Inject
    ReferenceIndex referenceIndex;
    
    @Inject
    DependencyIndex dependencyIndex;
    
    @Inject
    FileIndex fileIndex;

    @Override
    public Uni<List<RelatedFile>> getRelatedFiles(RelatedFileRequest request) {
        // In a real implementation, this orchestrator would query FileIndex, ReferenceIndex, 
        // and DependencyIndex to gather signals, score them, and return a ranked list.
        // Returning empty list as a structural placeholder.
        return Uni.createFrom().item(new ArrayList<>());
    }
}
