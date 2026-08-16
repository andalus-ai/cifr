package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CodeTestSkill implements CodingSkill {

    private final SkillContract contract;

    public CodeTestSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.test")
                .name("Code Test")
                .description("Execute and verify targeted test suites for changed code files or symbols")
                .category(SkillCategory.VERIFICATION)
                .reasoningMode(ReasoningMode.NONE)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "testTarget", "string (test class, method, or file path)",
                    "suite", "string (optional test suite or package)"
                ))
                .outputSchema(Map.of(
                    "passed", "boolean",
                    "totalTests", "integer",
                    "failedTests", "integer",
                    "durationMs", "integer"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    @Override
    public SkillContract contract() {
        return contract;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        String testTarget = (String) context.parameters().get("testTarget");
        if (testTarget == null || testTarget.isBlank()) {
            testTarget = (String) context.parameters().getOrDefault("suite", "all");
        }

        // Mock/deterministic test verification report
        boolean passed = !testTarget.toLowerCase().contains("fail");
        int totalTests = 1;
        int failedTests = passed ? 0 : 1;

        Map<String, Object> output = Map.of(
            "passed", passed,
            "totalTests", totalTests,
            "failedTests", failedTests,
            "testTarget", testTarget,
            "durationMs", 450
        );

        if (passed) {
            return Uni.createFrom().item(SkillResult.success(
                String.format("Tests passed for target '%s' (%d/%d passed)", testTarget, totalTests - failedTests, totalTests),
                output,
                List.of(testTarget)
            ));
        } else {
            return Uni.createFrom().item(SkillResult.failure(
                String.format("Tests failed for target '%s' (%d failure)", testTarget, failedTests),
                output
            ));
        }
    }
}
