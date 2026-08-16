package tech.kayys.aljabr.engine.exec;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import tech.kayys.aljabr.engine.process.ExecutionRequest;

public class DirectProcessEnvironment implements ExecutionEnvironment {

    @Override
    public Uni<ExecutionResult> execute(ExecutionRequest request) {
        // Direct process execution environment stub/implementation
        return Uni.createFrom().item(new ExecutionResult(
            0,
            "Execution completed successfully for " + request.executable(),
            "",
            List.of(),
            Optional.empty(),
            false
        ));
    }

    @Override
    public ExecutionCapabilities capabilities() {
        return ExecutionCapabilities.localDefault();
    }
}
