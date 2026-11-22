package tech.kayys.cifr.engine.verification;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.exec.intelligence.ProcessResult;
import tech.kayys.cifr.engine.exec.intelligence.ProcessRunner;
import tech.kayys.cifr.engine.exec.intelligence.ProcessSpec;
import tech.kayys.cifr.engine.exec.intelligence.ProcessStatus;
import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;
import tech.kayys.cifr.engine.verification.graph.ExecutionCheckStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessVerificationProviderTest {

    @Test
    void zeroExitCodeProducesPassingEvidenceBoundToRequest() {
        ProcessVerificationProvider provider = new ProcessVerificationProvider(new StubProcessRunner(
                new ProcessResult(0, Duration.ofSeconds(1), ProcessStatus.SUCCESS, "ok", "")));

        VerificationExecutionResult result = provider.execute(request()).await().indefinitely();

        assertEquals(ExecutionCheckStatus.PASSED, result.evidence().status());
        assertEquals(0, result.evidence().exitCode());
        assertEquals("task-1", result.evidence().taskId());
        assertEquals("workspace-1", result.evidence().workspaceId());
        assertEquals("sha256:revision-1", result.evidence().workspaceFingerprint());
        assertEquals("compile", result.evidence().checkId());
        assertEquals("ok", result.stdout());
    }

    @Test
    void nonZeroExitCodeProducesFailedEvidence() {
        ProcessVerificationProvider provider = new ProcessVerificationProvider(new StubProcessRunner(
                new ProcessResult(2, Duration.ofSeconds(1), ProcessStatus.FAILED, "", "compile failed")));

        VerificationExecutionResult result = provider.execute(request()).await().indefinitely();

        assertEquals(ExecutionCheckStatus.FAILED, result.evidence().status());
        assertEquals(2, result.evidence().exitCode());
    }

    @Test
    void timeoutDoesNotProducePassingEvidence() {
        ProcessVerificationProvider provider = new ProcessVerificationProvider(new StubProcessRunner(
                new ProcessResult(-1, Duration.ofSeconds(5), ProcessStatus.TIMEOUT, "", "timed out")));

        VerificationExecutionResult result = provider.execute(request()).await().indefinitely();

        assertEquals(ExecutionCheckStatus.ERROR, result.evidence().status());
        assertEquals(null, result.evidence().exitCode());
    }

    private static VerificationCheckRequest request() {
        return new VerificationCheckRequest(
                "task-1", "workspace-1", "sha256:revision-1",
                "compile", "test-process-provider",
                List.of("mvn", "-q", "compile"),
                Path.of("."),
                java.util.Map.of(),
                ExecutionBudget.testBudget());
    }

    private static final class StubProcessRunner implements ProcessRunner {
        private final ProcessResult result;

        private StubProcessRunner(ProcessResult result) {
            this.result = result;
        }

        @Override
        public Uni<ProcessResult> execute(ProcessSpec spec) {
            return Uni.createFrom().item(result);
        }

        @Override
        public Uni<Void> cancel(String processId) {
            return Uni.createFrom().voidItem();
        }
    }
}
