package tech.kayys.aljabr.engine.patch.mutation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MutationEngineTest {

    private Path tempWorkspace;
    private MutationEngine mutationEngine;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("mutation-test-");
        mutationEngine = new DefaultMutationEngine();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempWorkspace != null && Files.exists(tempWorkspace)) {
            try (var stream = Files.walk(tempWorkspace)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void testApplyModificationAndRollback() throws IOException {
        Path file = tempWorkspace.resolve("Sample.java");
        Files.writeString(file, "public class Sample {\n    int value = 10;\n}");

        TextEdit edit = new TextEdit("int value = 10;", "int value = 42;");
        FileEdit fileEdit = new FileEdit(Path.of("Sample.java"), List.of(edit));
        EditProposal proposal = new EditProposal("Update value", List.of(fileEdit), List.of(), List.of());

        MutationResult result = mutationEngine.apply(proposal, tempWorkspace).await().indefinitely();

        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("public class Sample {\n    int value = 42;\n}", Files.readString(file));

        // Now rollback
        mutationEngine.rollback(result.id(), tempWorkspace).await().indefinitely();
        assertEquals("public class Sample {\n    int value = 10;\n}", Files.readString(file));
    }

    @Test
    void testStaleEditDetection() throws IOException {
        Path file = tempWorkspace.resolve("Sample.java");
        Files.writeString(file, "public class Sample {\n    int value = 99;\n}");

        TextEdit edit = new TextEdit("int value = 10;", "int value = 42;");
        FileEdit fileEdit = new FileEdit(Path.of("Sample.java"), List.of(edit));
        EditProposal proposal = new EditProposal("Update stale value", List.of(fileEdit), List.of(), List.of());

        Exception ex = assertThrows(IllegalStateException.class, () -> {
            mutationEngine.apply(proposal, tempWorkspace).await().indefinitely();
        });

        assertTrue(ex.getMessage().contains("Stale edit rejected"));
        assertEquals("public class Sample {\n    int value = 99;\n}", Files.readString(file));
    }

    @Test
    void testCreateNewFileAndRollback() throws IOException {
        Path newFile = tempWorkspace.resolve("NewService.java");
        assertFalse(Files.exists(newFile));

        TextEdit edit = new TextEdit("", "public class NewService {}");
        FileEdit fileEdit = new FileEdit(Path.of("NewService.java"), List.of(edit));
        EditProposal proposal = new EditProposal("Create new file", List.of(fileEdit), List.of(), List.of());

        MutationResult result = mutationEngine.apply(proposal, tempWorkspace).await().indefinitely();

        assertTrue(Files.exists(newFile));
        assertEquals("public class NewService {}", Files.readString(newFile));

        // Rollback creation
        mutationEngine.rollback(result.id(), tempWorkspace).await().indefinitely();
        assertFalse(Files.exists(newFile));
    }
}
