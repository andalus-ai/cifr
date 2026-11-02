package tech.kayys.cifr.engine.tool.registry;
import java.util.List;
public record WorkspaceProfile(
    List<String> languages,
    List<String> frameworks,
    List<String> buildSystems,
    List<String> testSystems,
    List<String> availableTools
) {
    public WorkspaceProfile { languages = languages != null ? List.copyOf(languages) : List.of(); frameworks = frameworks != null ? List.copyOf(frameworks) : List.of(); buildSystems = buildSystems != null ? List.copyOf(buildSystems) : List.of(); testSystems = testSystems != null ? List.copyOf(testSystems) : List.of(); availableTools = availableTools != null ? List.copyOf(availableTools) : List.of(); }
}
