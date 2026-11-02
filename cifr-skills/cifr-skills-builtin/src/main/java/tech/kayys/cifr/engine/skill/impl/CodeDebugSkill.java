package tech.kayys.cifr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.patch.RiskLevel;
import tech.kayys.cifr.engine.skill.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeDebugSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.debug");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Debug",
        "Diagnoses errors, failure fingerprints, test breakdowns, and stack traces with scientific root-cause analysis",
        SkillCategory.DEBUGGING,
        RiskLevel.LOW,
        ReasoningMode.ESCALATABLE,
        false,
        Set.of("debug.diagnose", "debug.fingerprint")
    );

    @Override
    public SkillId id() {
        return ID;
    }

    @Override
    public SkillMetadata metadata() {
        return METADATA;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        return Uni.createFrom().item(() -> {
            String errorType = (String) context.parameters().getOrDefault("errorType", "");
            String message = (String) context.parameters().getOrDefault("message", "");
            String stackTrace = (String) context.parameters().getOrDefault("stackTrace", "");
            String failureId = (String) context.parameters().getOrDefault("failureId", "");
            String errorLog = (String) context.parameters().getOrDefault("errorLog", "");

            String target = !failureId.isBlank() ? failureId : (!errorType.isBlank() ? errorType : "session");
            String combined = (errorType + " " + message + " " + stackTrace + " " + errorLog).toLowerCase(Locale.ROOT);
            String failureCategory;
            String hypothesis;
            List<String> suggestedActions;

            if (combined.contains("nullpointerexception")) {
                failureCategory = "NPE / Uninitialized Reference";
                hypothesis = "An object reference was accessed before initialization or a dependency failed injection.";
                suggestedActions = List.of(
                    "Add null check or use Optional.ofNullable()",
                    "Verify CDI @Inject injection points and bean scope",
                    "Check mock initialization in test setup"
                );
            } else if (combined.contains("assertionerror") || combined.contains("assertionfailederror") || combined.contains("assert")) {
                failureCategory = "Contract / Assertion Mismatch";
                hypothesis = "Computed result diverged from expected behavioral specification.";
                suggestedActions = List.of(
                    "Compare actual output against test expectation",
                    "Inspect state transformations in recent change hunk",
                    "Verify edge cases with boundary conditions"
                );
            } else if (combined.contains("classnotfoundexception") || combined.contains("noclassdeffounderror") || combined.contains("cannot find symbol")) {
                failureCategory = "Classpath / Dependency Missing";
                hypothesis = "A required class or interface is not available in the module's classpath.";
                suggestedActions = List.of(
                    "Add dependency in module pom.xml",
                    "Verify package declaration and import statement",
                    "Run 'mvn clean install' on upstream module"
                );
            } else if (combined.contains("timeout") || combined.contains("timed out")) {
                failureCategory = "Timeout / Deadlock";
                hypothesis = "An asynchronous Mutiny pipeline or lock contention blocked execution.";
                suggestedActions = List.of(
                    "Increase duration timeout boundary",
                    "Check Mutiny reactive chain for unfulfilled subscribers",
                    "Inspect thread locks and concurrent hash maps"
                );
            } else {
                failureCategory = "General Runtime Failure";
                hypothesis = "Execution error during phase evaluation: " + target;
                suggestedActions = List.of(
                    "Inspect execution logs and stack trace",
                    "Add diagnostic logging at execution boundary"
                );
            }

            Map<String, Object> output = new HashMap<>();
            output.put("target", target);
            output.put("failureCategory", failureCategory);
            output.put("hypothesis", hypothesis);
            output.put("suggestedActions", suggestedActions);
            output.put("errorType", errorType);
            output.put("message", message);

            String summary = String.format("Debug session completed for %s: %s", target, failureCategory);
            return SkillResult.success(summary, output);
        });
    }
}
