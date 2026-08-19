package tech.kayys.aljabr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.exec.ExecutionResult;

public interface ExecutionHandle {

    Uni<ExecutionResult> await();

    Uni<Void> cancel();
}
