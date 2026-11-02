package tech.kayys.cifr.engine.exec.intelligence;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.Optional;

@ApplicationScoped
public class DefaultExecutionIntelligence implements ExecutionIntelligence {

    @Inject
    ProcessRunner processRunner;

    @Inject
    OutputNormalizer outputNormalizer;

    @Inject
    FailureClassifier failureClassifier;

    @Inject
    FailureFingerprintBuilder fingerprintBuilder;

    @Override
    public Uni<ExecutionOutcome> execute(String executionId, ProcessSpec spec, ProjectToolchain toolchain) {
        return processRunner.execute(spec).map(result -> {
            NormalizedOutput normalized = outputNormalizer.normalize(result, toolchain);
            FailureAnalysis failure = failureClassifier.classify(normalized);
            Optional<FailureFingerprint> fingerprint = result.isSuccess()
                ? Optional.empty()
                : Optional.of(fingerprintBuilder.build(failure));

            OutputSummary summary = new OutputSummary(
                result.status(),
                result.exitCode(),
                result.duration(),
                failure.failureType(),
                normalized.diagnostics(),
                normalized.failedTests(),
                normalized.passedTests(),
                normalized.relevantLogSnippets(),
                fingerprint
            );

            return new ExecutionOutcome(
                executionId,
                result,
                normalized,
                failure,
                summary,
                fingerprint
            );
        });
    }
}
