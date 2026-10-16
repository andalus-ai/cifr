package tech.kayys.aljabr.engine.patch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class PatchValidatorTest {

    private final Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
    private final Path testDir = workspaceRoot.resolve("test-temp-validator");
    private final Path testFile = testDir.resolve("vtest.txt");

    @AfterEach
    public void cleanup() throws IOException {
        if (Files.exists(testFile)) Files.delete(testFile);
        if (Files.exists(testDir)) Files.delete(testDir);
    }

    @Test
    public void testValidateContainsExpectedText() throws Exception {
        Files.createDirectories(testDir);
        String original = "alpha\nbeta\nexecute();\ngamma\n";
        Files.writeString(testFile, original);

        PatchEdit edit = new PatchEdit("execute();", "executeSafe();");
        PatchOperation patch = new PatchOperation("pv-1", workspaceRoot.relativize(testFile).toString(), java.util.List.of(edit), "validator test", null, null);

        PatchValidator validator = new PatchValidator();
        PatchValidationResult res = validator.validate(patch);
        assertTrue(res.isValid(), "Patch should be valid when expectedText exists");
    }

    @Test
    public void testValidateMissingExpectedText() throws Exception {
        Files.createDirectories(testDir);
        String original = "a\nb\nno-match\n";
        Files.writeString(testFile, original);

        PatchEdit edit = new PatchEdit("something not there", "x");
        PatchOperation patch = new PatchOperation("pv-2", workspaceRoot.relativize(testFile).toString(), java.util.List.of(edit), "validator test", null, null);

        PatchValidator validator = new PatchValidator();
        PatchValidationResult res = validator.validate(patch);
        assertFalse(res.isValid(), "Patch should be invalid when expectedText missing");
    }
}
