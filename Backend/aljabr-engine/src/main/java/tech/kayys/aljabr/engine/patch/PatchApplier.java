package tech.kayys.aljabr.engine.patch;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.repo.ChangeRecord;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.aljabr.engine.repo.ChangeType;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;
import tech.kayys.wayang.extension.Id;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class PatchApplier {

    private static final Logger log = Logger.getLogger(PatchApplier.class.getName());
    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @jakarta.inject.Inject
    PatchValidator validator;

    @jakarta.inject.Inject
    GitRepositoryService gitService;

    public AppliedPatch apply(PatchOperation patch) {
        PatchValidationResult validation = validator.validate(patch);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid patch: " + String.join(", ", validation.errors()));
        }

        Path absoluteTargetPath = WORKSPACE_ROOT.resolve(patch.path()).normalize();
        List<ChangeRecord> records = new ArrayList<>();

        try {
            String currentContent = Files.readString(absoluteTargetPath);
            String beforeHash = String.valueOf(currentContent.hashCode());
            
            String newContent = currentContent;
            for (PatchEdit edit : patch.edits()) {
                newContent = newContent.replace(edit.expectedText(), edit.replacementText());
            }

            Files.writeString(absoluteTargetPath, newContent);
            String afterHash = String.valueOf(newContent.hashCode());

            ChangeRecord record = new ChangeRecord(
                patch.path(),
                ChangeType.MODIFIED,
                beforeHash,
                afterHash,
                patch.id(),
                Id.random().asString()
            );
            records.add(record);

        } catch (IOException e) {
            throw new RuntimeException("Failed to apply patch to file: " + e.getMessage(), e);
        }

        RepositorySnapshot newSnapshot = gitService.captureSnapshot(WORKSPACE_ROOT);
        return new AppliedPatch(patch.id(), new ChangeSet(records), newSnapshot);
    }
}
