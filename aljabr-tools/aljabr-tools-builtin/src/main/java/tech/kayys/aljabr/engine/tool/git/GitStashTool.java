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
public class GitStashTool implements AljabrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

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
                String action = (String) request.arguments().get("action");
                String message = (String) request.arguments().get("message");
                int index = request.arguments().containsKey("index")
                    ? ((Number) request.arguments().get("index")).intValue() : 0;
                boolean keepIndex = Boolean.TRUE.equals(request.arguments().get("keepIndex"));

                return switch (action.toLowerCase()) {
                    case "list" -> listStashes();
                    case "push" -> pushStash(message, keepIndex);
                    case "pop" -> applyOrPop(index, true);
                    case "apply" -> applyOrPop(index, false);
                    case "drop" -> dropStash(index);
                    default -> new GitStashResult(false, "Unknown action: " + action, List.of());
                };
            } catch (Exception e) {
                return new GitStashResult(false, "Error: " + e.getMessage(), List.of());
            }
        });
    }

    private GitStashResult listStashes() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "stash", "list");
        pb.directory(WORKSPACE_ROOT.toFile());
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

    private GitStashResult pushStash(String message, boolean keepIndex) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "stash", "push"));
        if (keepIndex) cmd.add("--keep-index");
        if (message != null && !message.isBlank()) { cmd.add("-m"); cmd.add(message); }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(WORKSPACE_ROOT.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        return exitCode == 0
            ? new GitStashResult(true, "Stashed changes" + (message != null ? ": " + message : ""), List.of())
            : new GitStashResult(false, "Failed to stash: " + output, List.of());
    }

    private GitStashResult applyOrPop(int index, boolean pop) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "stash", pop ? "pop" : "apply"));
        if (index > 0) cmd.add("stash@{" + index + "}");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(WORKSPACE_ROOT.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        String verb = pop ? "Popped" : "Applied";
        return exitCode == 0
            ? new GitStashResult(true, verb + " stash@{" + index + "}", List.of())
            : new GitStashResult(false, "Failed to " + verb.toLowerCase() + ": " + output, List.of());
    }

    private GitStashResult dropStash(int index) throws Exception {
        String ref = "stash@{" + index + "}";
        ProcessBuilder pb = new ProcessBuilder("git", "stash", "drop", ref);
        pb.directory(WORKSPACE_ROOT.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        return exitCode == 0
            ? new GitStashResult(true, "Dropped " + ref, List.of())
            : new GitStashResult(false, "Failed to drop: " + output, List.of());
    }
}
