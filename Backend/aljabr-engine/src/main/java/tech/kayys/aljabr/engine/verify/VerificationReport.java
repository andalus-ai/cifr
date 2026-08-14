package tech.kayys.aljabr.engine.verify;

import java.util.List;

public record VerificationReport(
    boolean passed,
    List<CheckResult> checks,
    List<Diagnostic> diagnostics
) {}
