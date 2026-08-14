package tech.kayys.aljabr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PatchValidator {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    public PatchValidationResult validate(PatchOperation patch) {
        List<String> errors = new ArrayList<>();

        Path targetPath = Path.of(patch.path());

        // 1. Path validation
        if (targetPath.isAbsolute()) {
            errors.add("Absolute paths are not allowed: " + patch.path());
        }

        if (targetPath.normalize().startsWith("..")) {
            errors.add("Path escapes workspace: " + patch.path());
        }

        Path absoluteTargetPath = WORKSPACE_ROOT.resolve(targetPath).normalize();
        if (!absoluteTargetPath.startsWith(WORKSPACE_ROOT)) {
            errors.add("Path is outside writable scope: " + absoluteTargetPath);
        }

        // Return early if path is fundamentally broken to avoid file reads
        if (!errors.isEmpty()) {
            return PatchValidationResult.invalid(errors);
        }

        // 2. File State validation
        if (!Files.exists(absoluteTargetPath)) {
            errors.add("Target file does not exist: " + targetPath);
            return PatchValidationResult.invalid(errors);
        }

        try {
            String currentContent = Files.readString(absoluteTargetPath);

            for (int i = 0; i < patch.edits().size(); i++) {
                PatchEdit edit = patch.edits().get(i);
                int matches = countOccurrences(currentContent, edit.expectedText());

                if (matches != 1) {
                    errors.add("Edit index " + i + " failed: Expected exactly 1 match for expectedText, found " + matches);
                }
            }

        } catch (IOException e) {
            errors.add("Failed to read target file: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            return PatchValidationResult.valid();
        } else {
            return PatchValidationResult.invalid(errors);
        }
    }

    private int countOccurrences(String source, String target) {
        if (target == null || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = source.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
