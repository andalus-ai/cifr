package tech.kayys.aljabr.engine.tool.sandbox;

import io.smallrye.mutiny.Uni;
import java.util.List;
import tech.kayys.aljabr.engine.exec.ExecutionResult;

public interface ExecutionSandbox {
    Uni<ExecutionResult> execute(List<String> command, SandboxPolicy policy);
}
