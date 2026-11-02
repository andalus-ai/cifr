package tech.kayys.cifr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;

public interface ChangePrecondition {

    Uni<PreconditionValidation> validate(SemanticChangeIntent intent, List<EditOperation> operations, Path workspaceRoot);
}
