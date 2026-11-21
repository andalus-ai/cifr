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
public class GitStatusTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("git.status", "Runs git status --porcelain in the active workspace",
                ToolCategory.GIT, Map.of("type", "object"),
                Set.of(ToolCapability.READ_ONLY), ToolRisk.NONE);
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                Path root = context.resolveWorkspacePath(".");
                ProcessBuilder builder = new ProcessBuilder("git", "status", "--porcelain");
                builder.directory(root.toFile());
                builder.redirectErrorStream(true);
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exit = process.waitFor();
                return new GitCommandResult(exit == 0, exit == 0 ? output : "git status failed: " + output);
            } catch (Exception e) {
                return new GitCommandResult(false, "git status failed: " + e.getMessage());
            }
        });
    }

    private record GitCommandResult(boolean success, String summary) implements ToolResult {
        @Override public boolean isSuccess() { return success; }
        @Override public String getSummary() { return summary; }
    }
}
