package tech.kayys.cifr.engine.tool.sandbox;

import io.smallrye.mutiny.Uni;
import java.util.List;
import tech.kayys.cifr.engine.exec.ExecutionResult;

public interface ExecutionSandbox {
    Uni<ExecutionResult> execute(List<String> command, SandboxPolicy policy);
}
