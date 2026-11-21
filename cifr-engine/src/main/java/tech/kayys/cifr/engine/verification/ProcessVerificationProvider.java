package tech.kayys.cifr.engine.verification;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.exec.intelligence.ProcessResult;
import tech.kayys.cifr.engine.exec.intelligence.ProcessRunner;
import tech.kayys.cifr.engine.exec.intelligence.ProcessSpec;
import tech.kayys.cifr.engine.exec.intelligence.ProcessStatus;
import tech.kayys.cifr.engine.verification.graph.ExecutionCheckStatus;
import tech.kayys.cifr.engine.verification.graph.ExecutionEvidence;

import java.time.Instant;
import java.util.UUID;

/**
 * Bridges the verification SPI to Cifr's shared process runner.
 *
 * <p>This provider does not claim OS-level sandbox isolation. The configured
 * ProcessRunner backend remains responsible for process execution controls.</p>
 */
@ApplicationScoped
public class ProcessVerificationProvider implements VerificationProvider {

    private final ProcessRunner processRunner;

    @Inject
    public ProcessVerificationProvider(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public Uni<VerificationExecutionResult> execute(VerificationCheckRequest request) {
        if (request == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("request must not be null"));
        }

        Instant startedAt = Instant.now();
        ProcessSpec spec = new ProcessSpec(
                request.command(),
                request.workingDirectory(),
                request.environment(),
                request.budget());

        return processRunner.execute(spec).onItem().transform(result -> toResult(request, result, startedAt));
    }

    private VerificationExecutionResult toResult(
            VerificationCheckRequest request,
            ProcessResult result,
            Instant startedAt) {
        Instant completedAt = Instant.now();
        ExecutionCheckStatus status = statusOf(result);
        Integer exitCode = result == null || result.status() == ProcessStatus.START_FAILED
                || result.status() == ProcessStatus.TIMEOUT
                || result.status() == ProcessStatus.CANCELLED
                || result.status() == ProcessStatus.RESOURCE_LIMIT
                || result.status() == ProcessStatus.UNKNOWN
                ? null : result.exitCode();

        ExecutionEvidence evidence = new ExecutionEvidence(
                UUID.randomUUID().toString(),
                request.taskId(),
                request.workspaceId(),
                request.workspaceFingerprint(),
                request.checkId(),
                request.verifierId(),
                request.command(),
                status,
                exitCode,
                null,
                null,
                startedAt,
                completedAt);

        return new VerificationExecutionResult(
                evidence,
                result == null ? "" : result.stdout(),
                result == null ? "Process runner returned no result" : result.stderr());
    }

    private static ExecutionCheckStatus statusOf(ProcessResult result) {
        if (result == null || result.status() == null) {
            return ExecutionCheckStatus.ERROR;
        }
        return switch (result.status()) {
            case SUCCESS -> result.exitCode() == 0
                    ? ExecutionCheckStatus.PASSED : ExecutionCheckStatus.ERROR;
            case FAILED -> ExecutionCheckStatus.FAILED;
            case TIMEOUT, CANCELLED, START_FAILED, RESOURCE_LIMIT, UNKNOWN -> ExecutionCheckStatus.ERROR;
        };
    }
}
