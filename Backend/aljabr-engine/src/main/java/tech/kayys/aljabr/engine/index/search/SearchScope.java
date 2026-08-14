package tech.kayys.aljabr.engine.index.search;

import tech.kayys.aljabr.engine.index.FileRole;
import java.util.List;

public record SearchScope(
    List<FileRole> allowedRoles,
    List<String> pathPrefixes
) {}
