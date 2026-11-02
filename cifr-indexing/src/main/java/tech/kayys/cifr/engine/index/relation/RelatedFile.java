package tech.kayys.cifr.engine.index.relation;

import java.nio.file.Path;
import java.util.List;

public record RelatedFile(
    Path path,
    double score,
    List<RelationReason> reasons
) {}
