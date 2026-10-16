package tech.kayys.aljabr.engine.patch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PatchApplierTest {

    private final Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
    private final Path testDir = workspaceRoot.resolve("test-temp");
    private final Path testFile = testDir.resolve("test.txt");

    @AfterEach
    public void cleanup() throws IOException {
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        if (Files.exists(testDir)) {
            Files.delete(testDir);
        }
        // remove mutation store entries if any
        Path store = workspaceRoot.resolve(".aljabr").resolve("mutations");
        if (Files.exists(store) && Files.isDirectory(store)) {
            try (var s = Files.list(store)) {
                s.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    @Test
    public void testApplyAndIdempotency() throws Exception {
        Files.createDirectories(testDir);
        String original = "line1\nexpiresAt > now\nline3\n";
        Files.writeString(testFile, original);

        PatchValidator validator = new PatchValidator();
        MutationStore store = new MutationStore();
        GitRepositoryService gitService = new GitRepositoryService();
        DefaultWorkspaceGuard guard = new DefaultWorkspaceGuard();
        // inject gitService into guard (package-private field)
        guard.gitService = gitService;

        PatchApplier applier = new PatchApplier();
        applier.validator = validator;
        applier.mutationStore = store;
        applier.workspaceGuard = guard;
        applier.gitService = gitService;

        PatchEdit edit = new PatchEdit("expiresAt > now\n", "expiresAt >= now\n");
        PatchOperation patch = new PatchOperation("patch-test-1", testFile.toAbsolutePath().toString(), List.of(edit), "test", null, null);

        AppliedPatch result = applier.apply(patch);
        assertNotNull(result);
        assertEquals("patch-test-1", result.patchId());
        assertNotNull(result.changes());
        assertFalse(result.changes().changes().isEmpty());

        String updated = Files.readString(testFile);
        assertTrue(updated.contains("expiresAt >= now"));

        // second apply should be idempotent (no changes)
        AppliedPatch result2 = applier.apply(patch);
        assertNotNull(result2);
        assertTrue(result2.changes().changes().isEmpty());
    }
}
