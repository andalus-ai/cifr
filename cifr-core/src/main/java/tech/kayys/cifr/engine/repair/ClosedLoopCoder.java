package tech.kayys.cifr.engine.repair;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.context.ContextPlan;

import java.nio.file.Path;

/**
 * Executes closed-loop context assembly, mutation, verification, and autonomous repair loops.
 */
public interface ClosedLoopCoder {

    Uni<RepairResult> executeLoop(
        ContextPlan plan,
        int maxAttempts,
        Path workspaceRoot,
        String projectId
    );
}
