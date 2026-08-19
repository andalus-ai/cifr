package tech.kayys.aljabr.engine.orchestrator;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import tech.kayys.aljabr.engine.exec.ExecutionResult;

public interface ToolOrchestrator {

    Uni<ExecutionResult> orchestrate(ToolIntent intent, Path workspaceRoot, ToolOrchestrationPolicy policy);
}
