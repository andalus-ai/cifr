package tech.kayys.aljabr.engine.exec;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.process.ExecutionRequest;

public interface ExecutionEnvironment {

    Uni<ExecutionResult> execute(ExecutionRequest request);

    ExecutionCapabilities capabilities();
}
