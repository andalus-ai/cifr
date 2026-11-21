package tech.kayys.cifr.engine.tools;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GitDiffTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("git.diff", "Runs git diff for a workspace-relative path",
                ToolCategory.GIT,
                Map.of("type", "object", "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Workspace-relative path")
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
                ProcessBuilder builder = new ProcessBuilder("git", "diff", "--", relative.isEmpty() ? "." : relative);
                builder.directory(root.toFile());
                builder.redirectErrorStream(true);
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exit = process.waitFor();
                return new GitCommandResult(exit == 0, exit == 0 ? output : "git diff failed: " + output);
            } catch (Exception e) {
                return new GitCommandResult(false, "git diff failed: " + e.getMessage());
            }
        });
    }

    private record GitCommandResult(boolean success, String summary) implements ToolResult {
        @Override public boolean isSuccess() { return success; }
        @Override public String getSummary() { return summary; }
    }
}
