package tech.kayys.cifr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DefaultChangePrecondition implements ChangePrecondition {

    @Override
    public Uni<PreconditionValidation> validate(SemanticChangeIntent intent, List<EditOperation> operations, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (operations == null || operations.isEmpty()) {
                return PreconditionValidation.failed("No edit operations specified");
            }

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (EditOperation op : operations) {
                Path targetFile = op.file();
                if (targetFile == null) {
                    errors.add("Operation file target is null");
                    continue;
                }

                Path resolvedPath = workspaceRoot != null && !targetFile.isAbsolute()
                    ? workspaceRoot.resolve(targetFile)
                    : targetFile;

                if (!Files.exists(resolvedPath)) {
                    errors.add("Target file does not exist: " + resolvedPath);
                    continue;
                }

                if (!Files.isWritable(resolvedPath)) {
                    errors.add("Target file is not writable: " + resolvedPath);
                }
            }

            return new PreconditionValidation(errors.isEmpty(), errors, warnings);
        });
    }
}
