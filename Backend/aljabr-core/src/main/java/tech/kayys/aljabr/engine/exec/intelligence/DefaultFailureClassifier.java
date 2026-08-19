package tech.kayys.aljabr.engine.exec.intelligence;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DefaultFailureClassifier implements FailureClassifier {

    @Override
    public FailureAnalysis classify(NormalizedOutput output) {
        if (output == null || output.status() == ProcessStatus.SUCCESS) {
            return new FailureAnalysis(FailureType.UNKNOWN, "Execution succeeded", java.util.List.of(), Optional.empty(), false);
        }

        if (output.status() == ProcessStatus.TIMEOUT) {
            return new FailureAnalysis(
                FailureType.TIMEOUT,
                "Process execution timed out",
                output.diagnostics(),
                Optional.of("Command hung or exceeded execution timeout limit"),
                true
            );
        }

        if (output.status() == ProcessStatus.RESOURCE_LIMIT) {
            return new FailureAnalysis(
                FailureType.RESOURCE,
                "Resource limit exceeded during execution",
                output.diagnostics(),
                Optional.of("Process exceeded allocated memory or CPU limits"),
                false
            );
        }

        if (!output.diagnostics().isEmpty()) {
            return new FailureAnalysis(
                FailureType.COMPILATION,
                "Compilation failed with " + output.diagnostics().size() + " diagnostic(s)",
                output.diagnostics(),
                output.primaryErrorMessage(),
                true
            );
        }

        if (!output.failedTests().isEmpty()) {
            return new FailureAnalysis(
                FailureType.TEST_ASSERTION,
                output.failedTests().size() + " test(s) failed: " + String.join(", ", output.failedTests()),
                output.diagnostics(),
                output.primaryErrorMessage(),
                true
            );
        }

        String rawError = output.primaryErrorMessage().orElse("").toLowerCase();
        if (rawError.contains("connection refused") || rawError.contains("network") || rawError.contains("unreachable")) {
            return new FailureAnalysis(FailureType.NETWORK, "Network connection failure", output.diagnostics(), output.primaryErrorMessage(), false);
        }

        if (rawError.contains("could not resolve dependency") || rawError.contains("not found in repository")) {
            return new FailureAnalysis(FailureType.DEPENDENCY, "Missing build dependency", output.diagnostics(), output.primaryErrorMessage(), true);
        }

        return new FailureAnalysis(
            FailureType.UNKNOWN,
            output.primaryErrorMessage().orElse("Process exited with code " + output.exitCode()),
            output.diagnostics(),
            output.primaryErrorMessage(),
            true
        );
    }
}
