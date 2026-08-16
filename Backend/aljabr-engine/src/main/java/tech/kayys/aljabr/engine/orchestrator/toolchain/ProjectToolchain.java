package tech.kayys.aljabr.engine.orchestrator.toolchain;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public record ProjectToolchain(
    BuildSystem buildSystem,
    Set<String> languages,
    Path rootDirectory,
    Map<String, String> metadata
) {
    public ProjectToolchain {
        languages = languages != null ? Set.copyOf(languages) : Set.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static ProjectToolchain generic(Path rootDirectory) {
        return new ProjectToolchain(BuildSystem.GENERIC, Set.of(), rootDirectory, Map.of());
    }
}
