package tech.kayys.cifr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.exec.ExecutionResult;

public interface ExecutionHandle {

    Uni<ExecutionResult> await();

    Uni<Void> cancel();
}
