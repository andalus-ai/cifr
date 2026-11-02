package tech.kayys.cifr.engine.verification;

import java.nio.file.Path;

public record TestFailureSummary(
    String testName,
    String errorType,
    String message,
    String stackTrace,
    Path failedLocation,
    int lineNumber
) {
    public static TestFailureSummary of(String testName, String errorType, String message) {
        return new TestFailureSummary(testName, errorType, message, "", null, 0);
    }
}
