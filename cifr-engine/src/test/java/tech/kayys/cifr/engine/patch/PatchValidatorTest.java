package tech.kayys.cifr.engine.patch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PatchValidatorTest {

    @TempDir
    Path workspaceRoot;

    private ToolExecutionContext context() {
        return ToolExecutionContext.of("validator-test", workspaceRoot.toString(), null,
                Optional.empty(), () -> {}, workspaceRoot);
    }

    @Test
    public void testValidateContainsExpectedText() throws IOException {
        Path testDir = Files.createDirectory(workspaceRoot.resolve("test-temp-validator"));
        Path testFile = testDir.resolve("vtest.txt");
        Files.writeString(testFile, "alpha\nbeta\nexecute();\ngamma\n");

        PatchEdit edit = new PatchEdit("execute();", "executeSafe();");
        PatchOperation patch = new PatchOperation("pv-1", "test-temp-validator/vtest.txt",
                List.of(edit), "validator test", null, null);

        PatchValidationResult result = new PatchValidator().validate(patch, context());
        assertTrue(result.isValid(), "Patch should be valid when expectedText exists");
    }

    @Test
    public void testValidateMissingExpectedText() throws IOException {
        Path testDir = Files.createDirectory(workspaceRoot.resolve("test-temp-validator"));
        Path testFile = testDir.resolve("vtest.txt");
        Files.writeString(testFile, "a\nb\nno-match\n");

        PatchEdit edit = new PatchEdit("something not there", "x");
        PatchOperation patch = new PatchOperation("pv-2", "test-temp-validator/vtest.txt",
                List.of(edit), "validator test", null, null);

        PatchValidationResult result = new PatchValidator().validate(patch, context());
        assertFalse(result.isValid(), "Patch should be invalid when expectedText missing");
    }
}
