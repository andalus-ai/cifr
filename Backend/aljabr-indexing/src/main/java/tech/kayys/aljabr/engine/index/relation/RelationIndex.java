package tech.kayys.aljabr.engine.index.relation;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface RelationIndex {
    
    Uni<List<RelatedFile>> getRelatedFiles(RelatedFileRequest request);

}
