package tech.kayys.cifr.engine.patch;

import java.util.List;

public record WorkspaceCheck(
    boolean ok,
    List<String> conflicts
) {}
