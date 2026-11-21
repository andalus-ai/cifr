package tech.kayys.cifr.engine.exec.intelligence;

import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;\nimport java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultProcessRunnerTest {

    private final DefaultProcessRunner runner = new DefaultProcessRunner();

    @Test
    void exposesProcessIdAndCapturesSuccessfulOutput() {
        ProcessResult result = runner.execute(spec(
                List.of("sh", "-c", "printf 'runner-ok'"),
                ExecutionBudget.quickBudget())).await().indefinitely();

        assertEquals(ProcessStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode());
        assertEquals("runner-ok", result.stdout());
        assertNotNull(result.processId());
    }

    @Test
    void enforcesCombinedOutputLimitAcrossBothStreams() {
        ExecutionBudget budget = new ExecutionBudget(Duration.ofSeconds(5), 12, 1024, 1);
        ProcessResult result = runner.execute(spec(
                List.of("sh", "-c", "printf '123456789'; printf 'abcdefghij' >&2"),
                budget)).await().indefinitely();

        assertTrue(result.stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + result.stderr().replace("\\n[OUTPUT TRUNCATED]", "").getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 12);
        assertTrue(result.stdout().contains("[OUTPUT TRUNCATED]")
                || result.stderr().contains("[OUTPUT TRUNCATED]"));
    }

    @Test
    void timeoutIsNotReportedAsSuccess() {
        ExecutionBudget budget = new ExecutionBudget(Duration.ofMillis(100), 1024, 1024, 1);
        ProcessResult result = runner.execute(spec(
                List.of("sh", "-c", "sleep 5"),
                budget)).await().indefinitely();

        assertEquals(ProcessStatus.TIMEOUT, result.status());
        assertFalse(result.isSuccess());
    }

    @Test\n    void cancellationUsesCallerKnownExecutionId() throws Exception {\n        String executionId = UUID.randomUUID().toString();\n        ProcessSpec request = new ProcessSpec(List.of("sh", "-c", "sleep 5"),\n                Path.of(".").toAbsolutePath(), Map.of(),\n                new ExecutionBudget(Duration.ofSeconds(10), 1024, 1024, 1), executionId);\n        var completion = runner.execute(request).subscribeAsCompletionStage();\n        Thread.sleep(150);\n        runner.cancel(executionId).await().indefinitely();\n        ProcessResult result = completion.toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);\n        assertEquals(ProcessStatus.CANCELLED, result.status());\n        assertEquals(executionId, result.processId());\n    }\n\n    private static ProcessSpec spec(List<String> command, ExecutionBudget budget) {
        return new ProcessSpec(command, Path.of(".").toAbsolutePath(), Map.of(), budget);
    }
}
