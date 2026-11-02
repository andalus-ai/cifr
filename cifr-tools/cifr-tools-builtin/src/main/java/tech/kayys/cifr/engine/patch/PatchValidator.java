package tech.kayys.cifr.engine.patch;

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
        // Allow absolute paths if they resolve inside the workspace root; otherwise reject.
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
            System.out.println("DEBUG PatchValidator reading file: " + absoluteTargetPath + " content:\n" + currentContent);

            for (int i = 0; i < patch.edits().size(); i++) {
                PatchEdit edit = patch.edits().get(i);
                System.out.println("DEBUG expectedText: [" + edit.expectedText() + "]");
                int matches = countOccurrences(currentContent, edit.expectedText());
                System.out.println("DEBUG matches=" + matches);

                if (matches < 1) {
                    errors.add("Edit index " + i + " failed: Expected at least 1 match for expectedText, found " + matches);
                } else if (matches > 1) {
                    // warn but allow — require the caller to be more specific in future
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
