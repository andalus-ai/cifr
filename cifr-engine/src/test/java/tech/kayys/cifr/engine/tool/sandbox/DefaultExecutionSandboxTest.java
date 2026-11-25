package tech.kayys.cifr.engine.tool.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.cifr.engine.exec.ExecutionResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DefaultExecutionSandboxTest {

    @TempDir
    Path workspace;

    @Test
    void timeoutTerminatesDescendantProcesses() {
        Path shell = Path.of("/bin/sh");
        assumeTrue(Files.isExecutable(shell), "This process-tree test requires a POSIX shell");

        SandboxPolicy policy = new SandboxPolicy(
                workspace,
                Duration.ofMillis(400),
                List.of(shell.toString()),
                List.of(),
                false,
                128L * 1024 * 1024);

        ExecutionResult result = new DefaultExecutionSandbox().execute(
                List.of(shell.toString(), "-c", "sleep 30 & echo $!; wait"),
                policy).await().indefinitely();

        assertTrue(result.timedOut(), "The command should hit its configured timeout");
        assertFalse(result.stdoutSummary().isBlank(), "The child PID should be captured before timeout");

        long childPid = Long.parseLong(result.stdoutSummary().trim().lines().findFirst().orElseThrow());
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false)
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for child process termination");
            }
        }

        assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false),
                "A timed-out command must not leave its background child running");
    }
}
