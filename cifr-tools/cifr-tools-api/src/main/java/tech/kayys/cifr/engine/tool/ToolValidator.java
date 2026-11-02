package tech.kayys.cifr.engine.tool;

import io.smallrye.mutiny.Uni;

public interface ToolValidator {
    Uni<ValidationResult> validate(ToolRequest request, ToolExecutionContext context);
}
