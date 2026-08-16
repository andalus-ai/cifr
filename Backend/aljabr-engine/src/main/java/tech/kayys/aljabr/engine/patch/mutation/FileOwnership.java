package tech.kayys.aljabr.engine.patch.mutation;

import java.nio.file.Path;

public record FileOwnership(
    Path path,
    ChangeOwner owner
) {}
