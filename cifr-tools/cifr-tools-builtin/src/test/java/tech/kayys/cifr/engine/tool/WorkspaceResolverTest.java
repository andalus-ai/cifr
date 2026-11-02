package tech.kayys.cifr.engine.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void invocationWorkspaceTakesPrecedenceOverProcessConfiguration() throws Exception {
        Path configured = Files.createDirectory(tempDir.resolve("configured"));
        Path invocation = Files.createDirectory(tempDir.resolve("invocation"));
        String previous = System.getProperty("workspace.root");
        try {
            System.setProperty("workspace.root", configured.toString());
            ToolExecutionContext context = ToolExecutionContext.of(
                    "task-1", "workspace-1", null, Optional.empty(), () -> {}, invocation);

            assertEquals(invocation.toAbsolutePath().normalize(),
                    WorkspaceResolver.resolveWorkspaceRoot(context));
        } finally {
            if (previous == null) System.clearProperty("workspace.root");
            else System.setProperty("workspace.root", previous);
        }
    }

    @Test
    void invalidExplicitWorkspaceFailsInsteadOfSilentlyFallingBack() {
        Path missing = tempDir.resolve("missing");
        ToolExecutionContext context = ToolExecutionContext.of(
                "task-1", "workspace-1", null, Optional.empty(), () -> {}, missing);

        assertThrows(IllegalArgumentException.class,
                () -> WorkspaceResolver.resolveWorkspaceRoot(context));
    }
}
