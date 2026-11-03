package tech.kayys.cifr.engine.tool.git;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GitBranchTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "git_branch",
            "Manage git branches: list, create, switch, or delete branches.",
            ToolCategory.GIT,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of("type", "string", "enum", List.of("list", "create", "switch", "delete"),
                        "description", "Branch operation"),
                    "name", Map.of("type", "string", "description", "Branch name"),
                    "from", Map.of("type", "string", "description", "Source ref for new branch"),
                    "force", Map.of("type", "boolean", "description", "Force operation")
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
                String name = (String) request.arguments().get("name");
                String from = (String) request.arguments().get("from");
                boolean force = Boolean.TRUE.equals(request.arguments().get("force"));

                return switch (action.toLowerCase()) {
                    case "list" -> listBranches(workspace);
                    case "create" -> createBranch(name, from, workspace);
                    case "switch", "checkout" -> switchBranch(name, workspace);
                    case "delete" -> deleteBranch(name, force, workspace);
                    default -> new GitBranchResult(false, "Unknown action: " + action, List.of());
                };
            } catch (Exception e) {
                return new GitBranchResult(false, "Error: " + e.getMessage(), List.of());
            }
        });
    }

    private GitBranchResult listBranches(Path workspace) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "branch", "-a", "-v");
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        List<GitBranchInfo> branches = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.trim().isEmpty()) continue;
            boolean current = line.startsWith("* ");
            String clean = line.replace("*", "").trim();
            int idx = clean.indexOf(' ');
            String branchName = idx > 0 ? clean.substring(0, idx) : clean;
            String info = idx > 0 ? clean.substring(idx).trim() : "";
            branches.add(new GitBranchInfo(branchName, current, info));
        }
        return new GitBranchResult(true, "Found " + branches.size() + " branches", branches);
    }

    private GitBranchResult createBranch(String name, String from, Path workspace) throws Exception {
        if (name == null || name.isBlank())
            return new GitBranchResult(false, "Branch name required", List.of());

        List<String> cmd = new ArrayList<>(List.of("git", "branch"));
        if (from != null && !from.isBlank()) cmd.add(from);
        cmd.add(name);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(WORKSPACE_ROOT.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        return exitCode == 0
            ? new GitBranchResult(true, "Created branch: " + name, List.of(new GitBranchInfo(name, false, "new")))
            : new GitBranchResult(false, "Failed to create: " + output, List.of());
    }

    private GitBranchResult switchBranch(String name, Path workspace) throws Exception {
        if (name == null || name.isBlank())
            return new GitBranchResult(false, "Branch name required", List.of());

        ProcessBuilder pb = new ProcessBuilder("git", "switch", name);
        pb.directory(WORKSPACE_ROOT.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        return exitCode == 0
            ? new GitBranchResult(true, "Switched to: " + name, List.of())
            : new GitBranchResult(false, "Failed to switch: " + output, List.of());
    }

    private GitBranchResult deleteBranch(String name, boolean force, Path workspace) throws Exception {
        if (name == null || name.isBlank())
            return new GitBranchResult(false, "Branch name required", List.of());

        List<String> cmd = new ArrayList<>(List.of("git", "branch", force ? "-D" : "-d", name));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(WORKSPACE_ROOT.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        return exitCode == 0
            ? new GitBranchResult(true, "Deleted branch: " + name, List.of())
            : new GitBranchResult(false, "Failed to delete: " + output, List.of());
    }
}
