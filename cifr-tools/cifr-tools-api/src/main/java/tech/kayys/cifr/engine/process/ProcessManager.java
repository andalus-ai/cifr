package tech.kayys.cifr.engine.process;

import io.smallrye.mutiny.Uni;
import java.util.List;
import tech.kayys.cifr.engine.exec.ExecutionResult;

public interface ProcessManager {

    Uni<ProcessHandleInfo> start(ExecutionRequest request);

    Uni<ExecutionResult> waitFor(ProcessId processId);

    Uni<Void> terminate(ProcessId processId);

    Uni<List<ProcessHandleInfo>> list();

    Uni<OutputArtifact> output(ProcessId processId);
}
