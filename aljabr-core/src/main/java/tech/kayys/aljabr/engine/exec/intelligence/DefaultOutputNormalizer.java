package tech.kayys.aljabr.engine.exec.intelligence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;
import tech.kayys.aljabr.engine.verify.Diagnostic;
import tech.kayys.aljabr.engine.verify.DiagnosticSeverity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DefaultOutputNormalizer implements OutputNormalizer {

    // Regex patterns for Java compiler errors: [ERROR] /path/to/File.java:[line,col] message
    private static final Pattern JAVAC_ERROR = Pattern.compile("\\[ERROR\\]\\s+([^:\\[]+):\\[(\\d+),(\\d+)\\]\\s+(.*)");
    // Regex pattern for Rust/Cargo errors: error[E0425]: cannot find value `x` in this scope --> src/main.rs:10:5
    private static final Pattern CARGO_LOCATION = Pattern.compile("-->\\s+([^:]+):(\\d+):(\\d+)");
    // Regex pattern for failed test
    private static final Pattern JUNIT_FAILED_TEST = Pattern.compile("(?:FAILED|FAILURE):\\s+([A-Za-z0-9_$.#]+)");

    @Override
    public NormalizedOutput normalize(ProcessResult result, ProjectToolchain toolchain) {
        if (result == null) {
            return new NormalizedOutput(ProcessStatus.UNKNOWN, -1, List.of(), List.of(), List.of(), Optional.empty(), List.of());
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        List<String> failedTests = new ArrayList<>();
        List<String> passedTests = new ArrayList<>();
        List<String> relevantSnippets = new ArrayList<>();
        String primaryError = null;

        String combinedOutput = (result.stdout() + "\n" + result.stderr()).trim();
        String[] lines = combinedOutput.split("\\R");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Check javac pattern
            Matcher javacMatcher = JAVAC_ERROR.matcher(trimmed);
            if (javacMatcher.find()) {
                String file = javacMatcher.group(1).trim();
                int lineNum = Integer.parseInt(javacMatcher.group(2));
                int colNum = Integer.parseInt(javacMatcher.group(3));
                String msg = javacMatcher.group(4).trim();
                diagnostics.add(new Diagnostic(DiagnosticSeverity.ERROR.name(), Path.of(file), lineNum, colNum, msg));
                if (primaryError == null) primaryError = msg;
                relevantSnippets.add(trimmed);
                continue;
            }

            // Check test failures
            Matcher testMatcher = JUNIT_FAILED_TEST.matcher(trimmed);
            if (testMatcher.find()) {
                String testName = testMatcher.group(1).trim();
                failedTests.add(testName);
                if (primaryError == null) primaryError = "Test failure in: " + testName;
                relevantSnippets.add(trimmed);
                continue;
            }

            // Capture error-indicating lines
            if (trimmed.contains("FAILURE") || trimmed.contains("FAILED") || trimmed.contains("Exception") || trimmed.contains("Error:")) {
                if (relevantSnippets.size() < 20) {
                    relevantSnippets.add(trimmed);
                }
                if (primaryError == null && (trimmed.startsWith("Error:") || trimmed.contains("Exception:"))) {
                    primaryError = trimmed;
                }
            }
        }

        if (primaryError == null && result.exitCode() != 0) {
            primaryError = "Process failed with exit code " + result.exitCode();
        }

        return new NormalizedOutput(
            result.status(),
            result.exitCode(),
            diagnostics,
            failedTests,
            passedTests,
            Optional.ofNullable(primaryError),
            relevantSnippets
        );
    }
}
