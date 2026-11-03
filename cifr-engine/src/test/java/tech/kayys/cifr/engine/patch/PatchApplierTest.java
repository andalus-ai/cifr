package tech.kayys.cifr.engine.patch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.cifr.engine.repo.GitRepositoryService;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PatchApplierTest {

    @TempDir
    Path workspaceRoot;

    private Path testDir() { return workspaceRoot.resolve("test-temp"); }
    private Path testFile() { return testDir().resolve("test.txt"); }

    private ToolExecutionContext context() {
        return ToolExecutionContext.of("patch-test", workspaceRoot.toString(), null,
                Optional.empty(), () -> {}, workspaceRoot);
    }

    @AfterEach
    public void cleanup() throws IOException {
        Path testFile = testFile();
        Path testDir = testDir();
        if (Files.exists(testFile)) Files.delete(testFile);
        if (Files.exists(testDir)) Files.delete(testDir);
    }

    @Test
    public void testApplyAndIdempotency() throws Exception {
        Files.createDirectories(testDir());
        String original = "line1\nexpiresAt > now\nline3\n";
        Files.writeString(testFile(), original);

        PatchValidator validator = new PatchValidator();
        MutationStore store = new MutationStore();
        GitRepositoryService gitService = new GitRepositoryService();
        DefaultWorkspaceGuard guard = new DefaultWorkspaceGuard();
        guard.gitService = gitService;

        PatchApplier applier = new PatchApplier();
        applier.validator = validator;
        applier.mutationStore = store;
        applier.workspaceGuard = guard;
        applier.gitService = gitService;

        PatchEdit edit = new PatchEdit("expiresAt > now\n", "expiresAt >= now\n");
        PatchOperation patch = new PatchOperation("patch-test-1", "test-temp/test.txt",
                List.of(edit), "test", null, null);

        AppliedPatch result = applier.apply(patch, context());
        assertNotNull(result);
        assertEquals("patch-test-1", result.patchId());
        assertNotNull(result.changes());
        assertFalse(result.changes().changes().isEmpty());
        assertTrue(Files.readString(testFile()).contains("expiresAt >= now"));

        AppliedPatch result2 = applier.apply(patch, context());
        assertNotNull(result2);
        assertTrue(result2.changes().changes().isEmpty());
    }
}
