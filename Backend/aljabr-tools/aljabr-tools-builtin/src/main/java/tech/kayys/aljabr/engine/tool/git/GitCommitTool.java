package tech.kayys.aljabr.engine.tool.git;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GitCommitTool implements AljabrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "git_commit",
            "Commit staged changes to the git repository.",
            ToolCategory.GIT,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "message", Map.of("type", "string", "description", "Commit message"),
                    "all", Map.of("type", "boolean", "description", "Commit all changes including untracked"),
                    "amend", Map.of("type", "boolean", "description", "Amend the last commit"),
                    "author", Map.of("type", "string", "description", "Override author (name <email>)")
                ),
                "required", List.of("message")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_GIT),
            ToolRisk.HIGH
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String message = (String) request.arguments().get("message");
                boolean all = Boolean.TRUE.equals(request.arguments().getOrDefault("all", false));
                boolean amend = Boolean.TRUE.equals(request.arguments().getOrDefault("amend", false));
                String author = (String) request.arguments().get("author");

                if (message == null || message.isBlank())
                    return new GitCommitResult(false, "Commit message is required", null, null);

                List<String> cmd = new ArrayList<>(List.of("git", "commit"));
                if (all) cmd.add("--all");
                if (amend) cmd.add("--amend");
                if (author != null && !author.isBlank()) cmd.add("--author=" + author);
                cmd.add("-m");
                cmd.add(message);

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(WORKSPACE_ROOT.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes());
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    ProcessBuilder hashPb = new ProcessBuilder("git", "rev-parse", "HEAD");
                    hashPb.directory(WORKSPACE_ROOT.toFile());
                    Process hashProcess = hashPb.start();
                    String hash = new String(hashProcess.getInputStream().readAllBytes()).trim();
                    hashProcess.waitFor();
                    return new GitCommitResult(true, "Committed successfully", hash, output);
                } else {
                    return new GitCommitResult(false, "Commit failed: " + output, null, output);
                }

            } catch (Exception e) {
                return new GitCommitResult(false, "Error: " + e.getMessage(), null, null);
            }
        });
    }
}
