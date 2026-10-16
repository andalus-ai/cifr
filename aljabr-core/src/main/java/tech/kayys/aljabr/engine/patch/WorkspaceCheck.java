package tech.kayys.aljabr.engine.patch;

import java.util.List;

public record WorkspaceCheck(
    boolean ok,
    List<String> conflicts
) {}
