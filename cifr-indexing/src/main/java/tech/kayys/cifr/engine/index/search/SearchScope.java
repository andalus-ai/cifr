package tech.kayys.cifr.engine.index.search;

import tech.kayys.cifr.engine.index.FileRole;
import java.util.List;

public record SearchScope(
    List<FileRole> allowedRoles,
    List<String> pathPrefixes
) {}
