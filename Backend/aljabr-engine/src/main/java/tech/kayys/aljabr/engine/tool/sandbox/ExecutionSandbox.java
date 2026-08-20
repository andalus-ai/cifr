package tech.kayys.aljabr.engine.tool.sandbox;
import io.smallrye.mutiny.Uni;
import java.util.List;
public interface ExecutionSandbox {
    Uni<ExecutionResult> execute(List<String> command, SandboxPolicy policy);
}
