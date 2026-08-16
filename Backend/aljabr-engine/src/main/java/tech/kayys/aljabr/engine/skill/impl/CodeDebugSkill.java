package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.skill.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CodeDebugSkill implements CodingSkill {

    private static final Pattern STACK_LINE_PATTERN = Pattern.compile("at\\s+([\\w\\.$]+)\\.([\\w$]+)\\(([\\w\\.]+):(\\d+)\\)");

    private final SkillContract contract;

    public CodeDebugSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.debug")
                .name("Code Debug")
                .description("Analyze failure logs, test trace errors, and link them to workspace symbols and files")
                .category(SkillCategory.ANALYSIS)
                .reasoningMode(ReasoningMode.ESCALATABLE)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "errorLog", "string (stack trace, compilation failure, or test error message)",
                    "targetFile", "string (optional target file)"
                ))
                .outputSchema(Map.of(
                    "traceItems", "array of identified stack frame elements",
                    "suspectFiles", "array of implicated file paths",
                    "requiresModel", "boolean"
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
        String errorLog = (String) context.parameters().get("errorLog");
        if (errorLog == null || errorLog.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Missing required parameter: 'errorLog'"));
        }

        List<Map<String, Object>> traceItems = new ArrayList<>();
        List<String> suspectFiles = new ArrayList<>();

        Matcher matcher = STACK_LINE_PATTERN.matcher(errorLog);
        while (matcher.find()) {
            String className = matcher.group(1);
            String methodName = matcher.group(2);
            String fileName = matcher.group(3);
            int lineNumber = Integer.parseInt(matcher.group(4));

            traceItems.add(Map.of(
                "class", className,
                "method", methodName,
                "file", fileName,
                "line", lineNumber
            ));

            if (!suspectFiles.contains(fileName)) {
                suspectFiles.add(fileName);
            }
        }

        String targetFile = (String) context.parameters().get("targetFile");
        if (targetFile != null && !suspectFiles.contains(targetFile)) {
            suspectFiles.add(targetFile);
        }

        Map<String, Object> output = Map.of(
            "traceItems", traceItems,
            "suspectFiles", suspectFiles,
            "errorSummary", errorLog.lines().findFirst().orElse("Error detected"),
            "requiresModel", traceItems.isEmpty() // if stack trace isn't straightforward, escalate to model
        );

        return Uni.createFrom().item(SkillResult.success(
            String.format("Identified %d stack frames across %d suspect files", traceItems.size(), suspectFiles.size()),
            output,
            suspectFiles
        ));
    }
}
