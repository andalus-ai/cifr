package tech.kayys.aljabr.engine;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable session metadata used to compose the coding-agent system prompt
 * without coupling prompt policy to terminal rendering.
 */
public record AljabrPromptContext(
        String profileId,
        Path workspace,
        String modelId,
        boolean memoryEnabled,
        boolean harnessEnabled,
        int maxSteps) {

    public AljabrPromptContext {
        profileId = normalize(profileId, "coding-agent");
        workspace = workspace == null
                ? Path.of(".").toAbsolutePath().normalize()
                : workspace.toAbsolutePath().normalize();
        modelId = normalize(modelId, "default");
        maxSteps = Math.max(1, maxSteps);
    }

    public String workspacePath() {
        return Objects.toString(workspace, ".");
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
