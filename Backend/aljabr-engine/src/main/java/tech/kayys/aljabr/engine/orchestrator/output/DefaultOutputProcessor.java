package tech.kayys.aljabr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DefaultOutputProcessor implements OutputProcessor {

    private static final Pattern JAVAC_ERROR_PATTERN = Pattern.compile("^\\[ERROR\\]\\s+(.*?):\\[(\\d+),\\d+\\]\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern CLANG_GCC_ERROR_PATTERN = Pattern.compile("^(.*?):(\\d+):\\d+:\\s+(?:fatal\\s+)?error:\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern CLANG_GCC_WARN_PATTERN = Pattern.compile("^(.*?):(\\d+):\\d+:\\s+warning:\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern TEST_FAIL_PATTERN = Pattern.compile("^\\[ERROR\\]\\s+Failures:\\s*(\\d+)|Assertion(Error|FailedError):\\s*(.*)|Test Case '.*?' failed|XCTAssert\\w* failed.*$", Pattern.MULTILINE);

    @Override
    public Uni<ProcessedOutput> process(int exitCode, String stdout, String stderr, long maxOutputBytes) {
        return Uni.createFrom().item(() -> {
            String combined = ((stdout != null ? stdout : "") + "\n" + (stderr != null ? stderr : "")).trim();
            List<ExecutionDiagnostic> diagnostics = new ArrayList<>();
            ExecutionFailureType failureType = null;

            // 0. Check if output is a SARIF JSON report
            if (combined.startsWith("{") && (combined.contains("\"runs\"") || combined.contains("\"results\"") || combined.contains("sarif"))) {
                diagnostics.addAll(SarifReportParser.parse(combined));
                if (!diagnostics.isEmpty()) {
                    boolean hasErrors = diagnostics.stream().anyMatch(d -> "ERROR".equals(d.severity()));
                    failureType = hasErrors ? ExecutionFailureType.BUILD_FAILURE : null;
                    if (hasErrors || exitCode != 0) {
                        String summary = "SARIF analysis found " + diagnostics.size() + " issues (" + (hasErrors ? "Errors present" : "Warnings only") + ")";
                        return new ProcessedOutput(summary, diagnostics, combined.length() > 1000 ? combined.substring(0, 1000) + "..." : combined, false, failureType);
                    }
                }
            }

            if (exitCode == 0) {
                String snippet = combined.length() > 1000 ? combined.substring(0, 1000) + "\n... [truncated]" : combined;
                return ProcessedOutput.clean("Execution completed successfully (exit code 0)", snippet);
            }

            // 1. Classification & Diagnostics Extraction
            if (combined.contains("command not found") || combined.contains("No such file or directory") || combined.contains("JAVA_HOME")) {
                failureType = ExecutionFailureType.ENVIRONMENT_FAILURE;
                diagnostics.add(ExecutionDiagnostic.error("Environment binary or path configuration missing", "ENVIRONMENT"));
            } else if (combined.contains("Permission denied") || combined.contains("AccessDeniedException")) {
                failureType = ExecutionFailureType.PERMISSION_FAILURE;
                diagnostics.add(ExecutionDiagnostic.error("Permission denied during execution", "SECURITY"));
            } else if (combined.contains("Could not resolve host") || combined.contains("Connection refused") || combined.contains("ETIMEDOUT")) {
                failureType = ExecutionFailureType.NETWORK_FAILURE;
                diagnostics.add(ExecutionDiagnostic.error("Network connectivity failure", "NETWORK"));
            } else if (combined.contains("[ERROR] COMPILATION ERROR") || combined.contains("Compilation failure") || combined.contains("cannot find symbol") || combined.contains(": error:")) {
                failureType = ExecutionFailureType.BUILD_FAILURE;

                // Check Java compiler pattern
                Matcher jm = JAVAC_ERROR_PATTERN.matcher(combined);
                while (jm.find()) {
                    String filePath = jm.group(1);
                    int line = Integer.parseInt(jm.group(2));
                    String msg = jm.group(3);
                    diagnostics.add(ExecutionDiagnostic.error(Path.of(filePath), line, msg, "COMPILER"));
                }

                // Check Clang / GCC / Swift compiler error pattern
                Matcher cm = CLANG_GCC_ERROR_PATTERN.matcher(combined);
                while (cm.find()) {
                    String filePath = cm.group(1);
                    int line = Integer.parseInt(cm.group(2));
                    String msg = cm.group(3);
                    String cat = filePath.endsWith(".swift") ? "SWIFTC" : "CLANG_GCC";
                    diagnostics.add(ExecutionDiagnostic.error(Path.of(filePath), line, msg, cat));
                }

                // Check Clang / GCC / Swift warnings
                Matcher wm = CLANG_GCC_WARN_PATTERN.matcher(combined);
                while (wm.find()) {
                    String filePath = wm.group(1);
                    int line = Integer.parseInt(wm.group(2));
                    String msg = wm.group(3);
                    diagnostics.add(ExecutionDiagnostic.warning(Path.of(filePath), line, msg));
                }

                if (diagnostics.isEmpty()) {
                    diagnostics.add(ExecutionDiagnostic.error("Compilation error detected in source code", "COMPILER"));
                }
            } else if (combined.contains("Failures:") || combined.contains("AssertionError") || combined.contains("test failure") || combined.contains("FAILED") || combined.contains("***Failed") || combined.contains("Test Case") || combined.contains("XCTAssert")) {
                failureType = ExecutionFailureType.TEST_FAILURE;
                Matcher m = TEST_FAIL_PATTERN.matcher(combined);
                while (m.find()) {
                    diagnostics.add(ExecutionDiagnostic.error(m.group(0), "TEST_ASSERTION"));
                }
                if (diagnostics.isEmpty()) {
                    diagnostics.add(ExecutionDiagnostic.error("Unit test assertion failure", "TEST"));
                }
            } else {
                failureType = ExecutionFailureType.TOOL_FAILURE;
                diagnostics.add(ExecutionDiagnostic.error("Tool execution failed with exit code " + exitCode, "EXECUTION"));
            }

            // Bounded snippet generation
            StringBuilder snippet = new StringBuilder();
            if (!diagnostics.isEmpty()) {
                snippet.append("--- Extracted Diagnostics ---\n");
                for (ExecutionDiagnostic diag : diagnostics) {
                    if (diag.file().isPresent()) {
                        snippet.append(diag.file().get().getFileName()).append(":").append(diag.line()).append(" -> ").append(diag.message()).append("\n");
                    } else {
                        snippet.append("- ").append(diag.message()).append("\n");
                    }
                }
            }

            snippet.append("\n--- Log Tail ---\n");
            int tailLimit = Math.min((int) maxOutputBytes, 1500);
            if (combined.length() > tailLimit) {
                snippet.append("...\n").append(combined.substring(combined.length() - tailLimit));
            } else {
                snippet.append(combined);
            }

            boolean isTruncated = combined.length() > maxOutputBytes;
            String summary = failureType + " (exit code " + exitCode + ", " + diagnostics.size() + " diagnostics)";

            return new ProcessedOutput(summary, diagnostics, snippet.toString(), isTruncated, failureType);
        });
    }
}
