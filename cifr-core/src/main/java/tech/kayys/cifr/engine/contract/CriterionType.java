package tech.kayys.cifr.engine.contract;

/**
 * Categories of criteria for validating task success.
 */
public enum CriterionType {
    TEST_PASS,
    COMPILATION_CLEAN,
    LINT_CLEAN,
    SECURITY_GATE_PASSED,
    FILE_MUTATED,
    CUSTOM_CHECK
}
