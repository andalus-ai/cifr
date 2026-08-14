package tech.kayys.aljabr.engine.repo;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GitRepositoryService {

    public RepositorySnapshot captureSnapshot(Path workspace) {
        try {
            String branch = runCommand(workspace, "git", "branch", "--show-current").trim();
            String commitHash = runCommand(workspace, "git", "rev-parse", "HEAD").trim();
            
            String statusOutput = runCommand(workspace, "git", "status", "--porcelain");
            boolean dirty = !statusOutput.trim().isEmpty();
            
            List<String> changedFiles = new ArrayList<>();
            if (dirty) {
                String[] lines = statusOutput.split("\n");
                for (String line : lines) {
                    if (line.length() > 3) {
                        changedFiles.add(line.substring(3).trim());
                    }
                }
            }
            
            return new RepositorySnapshot(commitHash, branch, dirty, changedFiles, List.of());
        } catch (Exception e) {
            // Fallback for non-git workspaces or errors
            return new RepositorySnapshot("unknown", "unknown", false, List.of(), List.of());
        }
    }

    private String runCommand(Path workspace, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspace.toFile());
        Process process = builder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        process.waitFor();
        return output.toString();
    }
}
