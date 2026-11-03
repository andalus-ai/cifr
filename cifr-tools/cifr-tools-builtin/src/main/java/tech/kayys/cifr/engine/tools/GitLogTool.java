package tech.kayys.cifr.engine.tools;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GitLogTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("git.log", "Runs git log for a workspace-relative path",
                ToolCategory.GIT,
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Workspace-relative path"),
                        "limit", Map.of("type", "integer", "description", "Number of commits (1-1000)")
                )),
                Set.of(ToolCapability.READ_ONLY), ToolRisk.NONE);
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                Path root = context.resolveWorkspacePath(".");
                String supplied = (String) request.arguments().getOrDefault("path", ".");
                Path target = context.resolveWorkspacePath(supplied);
                String relative = root.relativize(target).toString();
                int limit = ((Number) request.arguments().getOrDefault("limit", 10)).intValue();
                if (limit < 1 || limit > 1000) {
                    return new GitCommandResult(false, "limit must be between 1 and 1000");
                }
                List<String> command = new ArrayList<>(List.of("git", "log", "-n", Integer.toString(limit)));
                command.add("--");
                command.add(relative.isEmpty() ? "." : relative);
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(root.toFile());
                builder.redirectErrorStream(true);
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exit = process.waitFor();
                return new GitCommandResult(exit == 0, exit == 0 ? output : "git log failed: " + output);
            } catch (Exception e) {
                return new GitCommandResult(false, "git log failed: " + e.getMessage());
            }
        });
    }

    private record GitCommandResult(boolean success, String summary) implements ToolResult {
        @Override public boolean isSuccess() { return success; }
        @Override public String getSummary() { return summary; }
    }
}
