package tech.kayys.aljabr.engine.verification;

public enum VerificationLevel {
    SYNTAX_L0(0, "Syntax / Patch Validity"),
    LINT_L1(1, "Formatting & Static Analysis"),
    TARGETED_TESTS_L2(2, "Targeted Unit Tests"),
    AFFECTED_TESTS_L3(3, "Affected Caller & Component Tests"),
    MODULE_TESTS_L4(4, "Full Module Test Suite"),
    FULL_SUITE_L5(5, "Full Workspace Verification Suite");

    private final int level;
    private final String description;

    VerificationLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int level() {
        return level;
    }

    public String description() {
        return description;
    }
}
