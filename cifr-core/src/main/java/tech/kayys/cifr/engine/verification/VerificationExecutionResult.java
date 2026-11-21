package tech.kayys.cifr.engine.verification;

import tech.kayys.cifr.engine.verification.graph.ExecutionEvidence;

import java.util.Objects;

/** Result of one provider execution, including bounded process output and provenance. */
public record VerificationExecutionResult(
        ExecutionEvidence evidence,
        String stdout,
        String stderr) {

    public VerificationExecutionResult {
        Objects.requireNonNull(evidence, "evidence");
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
