package tech.kayys.aljabr.engine.verification;

/**
 * Ordered stages in the multi-stage verification pipeline.
 * Cheap evidence (syntax, static analysis, compile) runs before expensive runtime tests.
 */
public enum VerificationStage {
    PARSE(0, "Parse & Syntax Check"),
    STATIC_ANALYSIS(1, "Static Analysis & Linting"),
    COMPILE(2, "Incremental Compilation"),
    TARGETED_TEST(3, "Targeted Unit Tests"),
    MODULE_TEST(4, "Module Test Suite"),
    INTEGRATION_TEST(5, "Integration Test Suite"),
    FULL_TEST(6, "Full Test Suite");

    private final int order;
    private final String description;

    VerificationStage(int order, String description) {
        this.order = order;
        this.description = description;
    }

    public int order() {
        return order;
    }

    public String description() {
        return description;
    }
}
