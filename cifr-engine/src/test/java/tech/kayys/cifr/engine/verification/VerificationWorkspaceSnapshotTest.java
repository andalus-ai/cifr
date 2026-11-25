package tech.kayys.cifr.engine.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VerificationWorkspaceSnapshotTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesWorkspaceWithoutCopyingGitMetadata() throws Exception {
        Path workspace = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        Files.writeString(workspace.resolve("README.md"), "source");
        Path git = Files.createDirectory(workspace.resolve(".git"));
        Files.writeString(git.resolve("config"), "private metadata");

        try (VerificationWorkspaceSnapshot snapshot = VerificationWorkspaceSnapshot.create(
                workspace, new VerificationWorkspaceSnapshot.Limits(10, 1024))) {
            assertEquals("source", Files.readString(snapshot.root().resolve("README.md")));
            assertFalse(Files.exists(snapshot.root().resolve(".git")));
            assertNotEquals(workspace, snapshot.root());
        }
    }

    @Test
    void rejectsSymbolicLinksInsteadOfCopyingTheirTargets() throws Exception {
        Path workspace = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "outside");
        try {
            Files.createSymbolicLink(workspace.resolve("linked.txt"), outside);
        } catch (UnsupportedOperationException | SecurityException | IOException unavailable) {
            return;
        }

        assertThrows(IOException.class, () -> VerificationWorkspaceSnapshot.create(
                workspace, new VerificationWorkspaceSnapshot.Limits(10, 1024)));
    }

    @Test
    void enforcesFileCountAndByteLimits() throws Exception {
        Path workspace = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        Files.writeString(workspace.resolve("one.txt"), "12345");
        Files.writeString(workspace.resolve("two.txt"), "67890");

        assertThrows(IOException.class, () -> VerificationWorkspaceSnapshot.create(
                workspace, new VerificationWorkspaceSnapshot.Limits(1, 1024)));
        assertThrows(IOException.class, () -> VerificationWorkspaceSnapshot.create(
                workspace, new VerificationWorkspaceSnapshot.Limits(10, 5)));
    }

    @Test
    void closeDeletesStagingDirectoryAndInvalidatesAccessor() throws Exception {
        Path workspace = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        Files.writeString(workspace.resolve("file.txt"), "content");

        VerificationWorkspaceSnapshot snapshot = VerificationWorkspaceSnapshot.create(
                workspace, new VerificationWorkspaceSnapshot.Limits(10, 1024));
        Path staged = snapshot.root();

        snapshot.close();

        assertFalse(Files.exists(staged));
        assertThrows(IllegalStateException.class, snapshot::root);
    }
}
