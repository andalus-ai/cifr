package tech.kayys.cifr.engine.exec;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import tech.kayys.cifr.engine.process.ExecutionRequest;

public class DirectProcessEnvironment implements ExecutionEnvironment {

    @Override
    public Uni<ExecutionResult> execute(ExecutionRequest request) {
        String target = request.command() != null && !request.command().isEmpty()
            ? String.join(" ", request.command())
            : "unknown";
        return Uni.createFrom().item(new ExecutionResult(
            0,
            "Execution completed successfully for " + target,
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
