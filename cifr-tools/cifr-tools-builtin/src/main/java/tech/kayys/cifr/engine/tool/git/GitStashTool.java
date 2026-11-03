package tech.kayys.cifr.engine.tool.git;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GitStashTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "git_stash",
            "Manage git stashes: list, push, pop, apply, or drop stashes.",
            ToolCategory.GIT,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of("type", "string", "enum", List.of("list", "push", "pop", "apply", "drop"),
                        "description", "Stash operation"),
                    "message", Map.of("type", "string", "description", "Stash message for push"),
                    "index", Map.of("type", "integer", "description", "Stash index for pop/apply/drop"),
                    "keepIndex", Map.of("type", "boolean", "description", "Keep index when stashing")
                ),
                "required", List.of("action")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_GIT),
            ToolRisk.MEDIUM
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                Path workspace = context.resolveWorkspacePath(".");
                String action = (String) request.arguments().get("action");
                String message = (String) request.arguments().get("message");
                int index = request.arguments().containsKey("index")
                    ? ((Number) request.arguments().get("index")).intValue() : 0;
                boolean keepIndex = Boolean.TRUE.equals(request.arguments().get("keepIndex"));

                return switch (action.toLowerCase()) {
                    case "list" -> listStashes(workspace);
                    case "push" -> pushStash(message, keepIndex, workspace);
                    case "pop" -> applyOrPop(index, true, workspace);
                    case "apply" -> applyOrPop(index, false, workspace);
                    case "drop" -> dropStash(index, workspace);
                    default -> new GitStashResult(false, "Unknown action: " + action, List.of());
                };
            } catch (Exception e) {
                return new GitStashResult(false, "Error: " + e.getMessage(), List.of());
            }
        });
    }

    private GitStashResult listStashes(Path workspace) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "stash", "list");
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor();

        List<GitStashInfo> stashes = new ArrayList<>();
        int idx = 0;
        for (String line : output.split("\n")) {
            if (!line.trim().isEmpty()) stashes.add(new GitStashInfo(idx++, line));
        }
        return new GitStashResult(true, "Found " + stashes.size() + " stash(es)", stashes);
    }

    private GitStashResult pushStash(String message, boolean keepIndex, Path workspace) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "stash", "push"));
        if (keepIndex) cmd.add("--keep-index");
        if (message != null && !message.isBlank()) { cmd.add("-m"); cmd.add(message); }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        return exitCode == 0
            ? new GitStashResult(true, "Stashed changes" + (message != null ? ": " + message : ""), List.of())
            : new GitStashResult(false, "Failed to stash: " + output, List.of());
    }

    private GitStashResult applyOrPop(int index, boolean pop, Path workspace) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "stash", pop ? "pop" : "apply"));
        if (index > 0) cmd.add("stash@{" + index + "}");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        String verb = pop ? "Popped" : "Applied";
        return exitCode == 0
            ? new GitStashResult(true, verb + " stash@{" + index + "}", List.of())
            : new GitStashResult(false, "Failed to " + verb.toLowerCase() + ": " + output, List.of());
    }

    private GitStashResult dropStash(int index, Path workspace) throws Exception {
        String ref = "stash@{" + index + "}";
        ProcessBuilder pb = new ProcessBuilder("git", "stash", "drop", ref);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        return exitCode == 0
            ? new GitStashResult(true, "Dropped " + ref, List.of())
            : new GitStashResult(false, "Failed to drop: " + output, List.of());
    }
}
