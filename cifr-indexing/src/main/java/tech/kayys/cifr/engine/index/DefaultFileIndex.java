package tech.kayys.cifr.engine.index;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@ApplicationScoped
public class DefaultFileIndex implements FileIndex {

    private final Map<String, FileRecord> fileRecords = new ConcurrentHashMap<>();

    @Inject
    FileClassifier classifier;

    @Override
    public Uni<WorkspaceFingerprint> computeFingerprint(Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            try {
                long fileCount = fileRecords.isEmpty() ? countFiles(workspaceRoot) : fileRecords.size();
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
                             String lang = classifier != null ? classifier.detectLanguage(p) : "unknown";
                             boolean isGenerated = classifier != null && classifier.isGenerated(p);
                             boolean isTest = classifier != null && classifier.isTest(p);
                             FileRole role = classifier != null ? classifier.classifyRole(p) : FileRole.SOURCE;
                             
                             FileRecord rec = new FileRecord(p, hash, size, lang, isGenerated, isTest, role);
                             registerFile(rec);
                             records.add(rec);
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
        return Uni.createFrom().item(fileRecords.values().stream()
                .filter(record -> workspaceId == null || workspaceId.equals(record.workspaceId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    }

    @Override
    public void registerFile(FileRecord record) {
        if (record != null && record.path() != null) {
            fileRecords.put(record.workspaceId() + "|" + record.path().normalize(), record);
        }
    }

    @Override
    public void removeFile(Path file, String workspaceId) {
        if (file == null || workspaceId == null || workspaceId.isBlank()) return;
        Path normalized = file.normalize();
        fileRecords.entrySet().removeIf(entry -> {
            FileRecord record = entry.getValue();
            if (!workspaceId.equals(record.workspaceId()) || record.path() == null) return false;
            Path indexed = record.path().normalize();
            return indexed.equals(normalized) || indexed.endsWith(normalized) || normalized.endsWith(indexed);
        });
    }

    @Override
    public void clear() {
        fileRecords.clear();
    }
    
    private boolean shouldInclude(Path path) {
        String pStr = path.toString();
        return !pStr.contains("/.git/") && !pStr.contains("/.idea/") && !pStr.contains("/target/");
    }

    private long countFiles(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return 0;
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
