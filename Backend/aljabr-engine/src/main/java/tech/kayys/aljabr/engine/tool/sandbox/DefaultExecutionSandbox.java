package tech.kayys.aljabr.engine.tool.sandbox;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
@ApplicationScoped
public class DefaultExecutionSandbox implements ExecutionSandbox {
    @Override
    public Uni<ExecutionResult> execute(List<String> command, SandboxPolicy policy) {
        return Uni.createFrom().emitter(emitter -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                if (policy.workDir() != null) pb.directory(policy.workDir().toFile());
                pb.redirectErrorStream(false);
                Process process = pb.start();
                long timeoutMs = policy.timeout() != null ? policy.timeout().toMillis() : 30_000L;
                boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    emitter.complete(new ExecutionResult(-1, "", "Timeout", true));
                    return;
                }
                String stdout = new String(process.getInputStream().readAllBytes());
                String stderr = new String(process.getErrorStream().readAllBytes());
                emitter.complete(new ExecutionResult(process.exitValue(), stdout, stderr, false));
            } catch (Exception e) {
                emitter.complete(new ExecutionResult(-1, "", e.getMessage(), false));
            }
        });
    }
}
