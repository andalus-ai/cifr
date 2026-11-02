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
    // Relative paths are unique only within a workspace.
    private final Map<String, FileRecord> fileRecords = new ConcurrentHashMap<>();

    @Inject FileClassifier classifier;

    @Override
    public Uni<WorkspaceFingerprint> computeFingerprint(Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            try {
                long fileCount = fileRecords.isEmpty() ? countFiles(workspaceRoot) : fileRecords.size();
                return new WorkspaceFingerprint("repo-hash-" + fileCount, "1.0", "cfg-hash-1");
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute fingerprint", e);
            }
        });
    }

    @Override
    public Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot) {
        return scanWorkspace(workspaceRoot, "default-workspace");
    }

    @Override
    public Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot, String workspaceId) {
        if (workspaceRoot == null || workspaceId == null || workspaceId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("workspace root and workspaceId are required"));
        }
        return Uni.createFrom().item(() -> {
            List<FileRecord> records = new ArrayList<>();
            Path root = workspaceRoot.toAbsolutePath().normalize();
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile).filter(this::shouldInclude).forEach(path -> {
                    try {
                        long size = Files.size(path);
                        String hash = computeHash(path);
                        String language = classifier == null ? "unknown" : classifier.detectLanguage(path);
                        boolean generated = classifier != null && classifier.isGenerated(path);
                        boolean test = classifier != null && classifier.isTest(path);
                        FileRole role = classifier == null ? FileRole.SOURCE : classifier.classifyRole(path);
                        FileRecord record = new FileRecord(root.relativize(path), hash, size, language,
                                generated, test, role, workspaceId);
                        registerFile(record);
                        records.add(record);
                    } catch (IOException ignored) {
                        // Unreadable files are omitted from this snapshot.
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan workspace " + root, e);
            }
            return records;
        });
    }

    @Override
    public Uni<List<FileRecord>> getCurrentIndex(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("workspaceId is required"));
        }
        return Uni.createFrom().item(() -> fileRecords.values().stream()
                .filter(record -> workspaceId.equals(record.workspaceId()))
                .toList());
    }

    @Override
    public void registerFile(FileRecord record) {
        if (record == null || record.path() == null || record.workspaceId() == null || record.workspaceId().isBlank()) return;
        fileRecords.put(fileKey(record.workspaceId(), record.path()), record);
    }

    @Override
    public void clear() {
        fileRecords.clear();
    }

    @Override
    public void clearWorkspace(String workspaceId) {
        if (workspaceId != null) fileRecords.keySet().removeIf(key -> key.startsWith(workspaceId + "|"));
    }

    private static String fileKey(String workspaceId, Path path) {
        return workspaceId + "|" + path.normalize();
    }

    private boolean shouldInclude(Path path) {
        String value = path.toString().replace('\\', '/');
        return !value.contains("/.git/") && !value.contains("/.idea/") && !value.contains("/target/");
    }

    private long countFiles(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return 0;
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(this::shouldInclude).count();
        }
    }

    private String computeHash(Path path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return "unknown-hash";
        }
    }
}
