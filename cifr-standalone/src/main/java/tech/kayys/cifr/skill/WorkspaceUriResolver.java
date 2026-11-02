package tech.kayys.cifr.skill;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Pluggable SPI for resolving language/package-specific URIs (e.g., package:riverpod/...)
 * to local filesystem paths inside a workspace.
 */
public interface WorkspaceUriResolver {

    /**
     * Checks if this resolver can handle the specified URI or path scheme.
     */
    boolean canResolve(String rawUri);

    /**
     * Resolves the raw URI to a local file path.
     */
    Optional<Path> resolve(String rawUri);

    /**
     * Provides a diagnostic description in case resolution fails.
     */
    String describeFailure(String rawUri);
}
