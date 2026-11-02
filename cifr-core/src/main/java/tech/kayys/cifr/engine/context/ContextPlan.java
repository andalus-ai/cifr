package tech.kayys.cifr.engine.context;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record ContextPlan(
    String taskId,
    String taskDescription,
    Optional<String> targetSymbol,
    Optional<Path> targetFile,
    Optional<String> failureTrace,
    List<String> relatedSymbols,
    ContextBudget budget
) {
    public static ContextPlan of(String taskId, String taskDescription) {
        return new ContextPlan(
            taskId,
            taskDescription,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            ContextBudget.defaultBudget()
        );
    }
}
