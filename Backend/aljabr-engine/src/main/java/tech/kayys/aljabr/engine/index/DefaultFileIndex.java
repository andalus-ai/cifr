package tech.kayys.aljabr.engine.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class DefaultFileIndex implements FileIndex {

    @Inject
    FileClassifier classifier;

    @Override
    public Uni<WorkspaceFingerprint> computeFingerprint(Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            try {
                // A very simple deterministic fingerprint based on file count and structure for R1
                long fileCount = countFiles(workspaceRoot);
                String fakeHash = "repo-hash-" + fileCount;
                return new WorkspaceFingerprint(fakeHash, "1.0", "cfg-hash-1");
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute fingerprint", e);
            }
        });
    }

    @Override
    public Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            List<FileRecord> records = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(workspaceRoot)) {
                paths.filter(Files::isRegularFile)
                     .filter(this::shouldInclude)
                     .forEach(p -> {
                         try {
                             long size = Files.size(p);
                             String hash = computeHash(p);
                             String lang = classifier.detectLanguage(p);
                             boolean isGenerated = classifier.isGenerated(p);
                             boolean isTest = classifier.isTest(p);
                             FileRole role = classifier.classifyRole(p);
                             
                             records.add(new FileRecord(p, hash, size, lang, isGenerated, isTest, role));
                         } catch (IOException e) {
                             // Ignore unreadable files
                         }
                     });
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan workspace", e);
            }
            return records;
        });
    }

    @Override
    public Uni<List<FileRecord>> getCurrentIndex(String workspaceId) {
        // For R1, we return empty or stub, since actual persistence to Redis would be done in the orchestrator
        return Uni.createFrom().item(new ArrayList<>());
    }
    
    private boolean shouldInclude(Path path) {
        String pStr = path.toString();
        return !pStr.contains("/.git/") && !pStr.contains("/.idea/");
    }

    private long countFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(this::shouldInclude).count();
        }
    }

    private String computeHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return "unknown-hash";
        }
    }
}
