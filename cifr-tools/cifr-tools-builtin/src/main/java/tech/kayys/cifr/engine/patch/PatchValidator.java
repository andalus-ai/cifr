package tech.kayys.cifr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PatchValidator {

    public PatchValidationResult validate(PatchOperation patch, ToolExecutionContext context) {
        List<String> errors = new ArrayList<>();
        Path absoluteTargetPath;
        try {
            absoluteTargetPath = context.resolveWorkspacePath(patch.path());
        } catch (RuntimeException e) {
            return PatchValidationResult.invalid(List.of("Path is outside workspace: " + patch.path()));
        }

        if (!Files.exists(absoluteTargetPath)) {
            errors.add("Target file does not exist: " + patch.path());
            return PatchValidationResult.invalid(errors);
        }

        try {
            String currentContent = Files.readString(absoluteTargetPath);
            for (int i = 0; i < patch.edits().size(); i++) {
                PatchEdit edit = patch.edits().get(i);
                int matches = countOccurrences(currentContent, edit.expectedText());
                if (matches < 1) {
                    errors.add("Edit index " + i + " failed: Expected at least 1 match for expectedText, found " + matches);
                }
            }
        } catch (IOException e) {
            errors.add("Failed to read target file: " + e.getMessage());
        }

        return errors.isEmpty() ? PatchValidationResult.valid() : PatchValidationResult.invalid(errors);
    }

    private int countOccurrences(String source, String target) {
        if (target == null || target.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
}
