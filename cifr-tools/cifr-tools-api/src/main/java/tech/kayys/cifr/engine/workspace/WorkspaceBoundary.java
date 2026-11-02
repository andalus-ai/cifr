package tech.kayys.cifr.engine.workspace;

import java.nio.file.Path;

public interface WorkspaceBoundary {
    Path root();
    Path resolveSafe(String relativePath) throws SecurityException;
    boolean contains(Path path);
}
