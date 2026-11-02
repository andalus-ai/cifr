package tech.kayys.cifr.engine.orchestrator.toolchain;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import tech.kayys.cifr.engine.orchestrator.adapter.ToolAdapter;

public record ProjectToolchain(
    BuildSystem buildSystem,
    TestFramework testFramework,
    Set<String> languages,
    List<ToolAdapter> adapters,
    Path rootDirectory
) {
    public ProjectToolchain {
        languages = languages != null ? Set.copyOf(languages) : Set.of();
        adapters = adapters != null ? List.copyOf(adapters) : List.of();
    }

    /** Convenience constructor matching the pre-existing 4-arg form used by ProjectDetector. */
    public static ProjectToolchain of(BuildSystem buildSystem, TestFramework testFramework,
                                      Set<String> languages, List<ToolAdapter> adapters) {
        return new ProjectToolchain(buildSystem, testFramework, languages, adapters, null);
    }
}
