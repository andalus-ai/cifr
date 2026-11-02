package tech.kayys.cifr.engine.exec;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.process.ExecutionRequest;

public interface ExecutionEnvironment {

    Uni<ExecutionResult> execute(ExecutionRequest request);

    ExecutionCapabilities capabilities();
}
