package tech.kayys.cifr.engine.orchestrator;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import tech.kayys.cifr.engine.exec.ExecutionResult;

public interface ToolOrchestrator {

    Uni<ExecutionResult> orchestrate(ToolIntent intent, Path workspaceRoot, ToolOrchestrationPolicy policy);
}
