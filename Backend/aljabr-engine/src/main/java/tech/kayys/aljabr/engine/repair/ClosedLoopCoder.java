package tech.kayys.aljabr.engine.repair;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.context.ContextPlan;

import java.nio.file.Path;

public interface ClosedLoopCoder {

    Uni<RepairResult> executeLoop(ContextPlan plan, int maxAttempts, Path workspaceRoot, String workspaceId);
}
