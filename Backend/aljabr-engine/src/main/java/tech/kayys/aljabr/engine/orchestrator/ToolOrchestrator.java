package tech.kayys.aljabr.engine.orchestrator;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.orchestrator.exec.ExecutionResult;

import java.nio.file.Path;

public interface ToolOrchestrator {

    Uni<ExecutionResult> executeIntent(ToolIntent intent, Path workspaceRoot);
}
