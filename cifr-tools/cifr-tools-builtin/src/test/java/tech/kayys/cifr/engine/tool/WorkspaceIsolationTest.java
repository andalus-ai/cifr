package tech.kayys.cifr.engine.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIsolationTest {

    @TempDir
    Path temp;

    private ToolExecutionContext context(Path root) {
        return ToolExecutionContext.of("task", root.toString(), null, Optional.empty(), () -> {}, root);
    }

    @Test
    void resolvesPathsWithinTheInvocationWorkspace() throws IOException {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path file = Files.createFile(workspace.resolve("source.txt"));

        assertEquals(file.toRealPath(), context(workspace).resolveWorkspacePath("source.txt"));
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() throws IOException {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        ToolExecutionContext context = context(workspace);

        assertThrows(SecurityException.class, () -> context.resolveWorkspacePath("../outside.txt"));
        assertThrows(SecurityException.class, () -> context.resolveWorkspacePath(temp.resolve("outside.txt").toString()));
    }

    @Test
    void isolatesConcurrentWorkspaceContexts() throws IOException {
        Path first = Files.createDirectory(temp.resolve("first"));
        Path second = Files.createDirectory(temp.resolve("second"));

        Path firstFile = context(first).resolveWorkspacePath("same.txt");
        Path secondFile = context(second).resolveWorkspacePath("same.txt");

        assertNotEquals(firstFile, secondFile);
        assertEquals(first.toRealPath(), firstFile.getParent());
        assertEquals(second.toRealPath(), secondFile.getParent());
    }

    @Test
    void rejectsSymlinkEscapesIncludingDanglingLinks() throws IOException {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");

        Path link = workspace.resolve("outside-link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable in this environment");
        }

        ToolExecutionContext context = context(workspace);
        assertThrows(SecurityException.class, () -> context.resolveWorkspacePath("outside-link/secret.txt"));

        Path dangling = workspace.resolve("dangling-link");
        Files.createSymbolicLink(dangling, temp.resolve("missing-outside-target"));
        assertThrows(IllegalStateException.class, () -> context.resolveWorkspacePath("dangling-link/new.txt"));
    }

    @Test
    void failsClosedWhenNoWorkspaceRootWasProvided() {
        ToolExecutionContext context = ToolExecutionContext.of(
                "task", "workspace", null, Optional.empty(), () -> {});
        assertThrows(IllegalStateException.class, () -> context.resolveWorkspacePath("file.txt"));
    }
}
