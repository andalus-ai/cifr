package tech.kayys.aljabr.engine.skill.routing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.skill.SkillContext;
import tech.kayys.aljabr.engine.skill.SkillDescriptor;
import tech.kayys.aljabr.engine.skill.SkillRegistry;
import tech.kayys.aljabr.engine.skill.reasoning.ReasoningDecision;
import tech.kayys.aljabr.engine.skill.reasoning.ReasoningGate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class SkillSelector {

    private static final Pattern SEARCH_PATTERN = Pattern.compile("(?i)^(?:find|where\\s+is|search|locate)\\s+(.+)");
    private static final Pattern INSPECT_PATTERN = Pattern.compile("(?i)^(?:inspect|definition\\s+of|show\\s+declaration|view\\s+symbol)\\s+(.+)");
    private static final Pattern TRACE_PATTERN = Pattern.compile("(?i)^(?:trace|references\\s+for|who\\s+calls|callers\\s+of)\\s+(.+)");
    private static final Pattern TEST_PATTERN = Pattern.compile("(?i)^(?:run\\s+test|execute\\s+test|test)\\s*(.*)");
    private static final Pattern EXPLAIN_PATTERN = Pattern.compile("(?i)^(?:explain|how\\s+does\\s+(?:the)?|summary\\s+of)\\s+(.+)");
    private static final Pattern REVIEW_PATTERN = Pattern.compile("(?i)^(?:review|check\\s+diff|verify\\s+changes)\\s*(.*)");
    private static final Pattern DEBUG_PATTERN = Pattern.compile("(?i)^(?:debug|fix\\s+error|exception\\s+in|investigate\\s+failure)\\s*(.*)");

    @Inject
    SkillRegistry skillRegistry;

    @Inject
    ReasoningGate reasoningGate;

    public SkillSelector() {
    }

    public SkillSelector(SkillRegistry skillRegistry, ReasoningGate reasoningGate) {
        this.skillRegistry = skillRegistry;
        this.reasoningGate = reasoningGate;
    }

    public Optional<SkillSelection> select(String query, SkillContext context) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String trimmed = query.trim();
        Map<String, Object> params = context != null ? new HashMap<>(context.parameters()) : new HashMap<>();

        // 1. Explicit skill invocation (e.g. /search foo, code.search, etc.)
        if (trimmed.startsWith("/") || trimmed.startsWith("code.")) {
            String[] parts = trimmed.split("\\s+", 2);
            String rawSkill = parts[0].startsWith("/") ? "code." + parts[0].substring(1) : parts[0];
            String arg = parts.length > 1 ? parts[1] : "";

            var skillOpt = skillRegistry.find(rawSkill);
            if (skillOpt.isPresent()) {
                SkillDescriptor desc = skillOpt.get().descriptor();
                if (!arg.isBlank()) {
                    params.put("query", arg);
                    params.put("symbol", arg);
                    params.put("testTarget", arg);
                }
                ReasoningDecision decision = reasoningGate.evaluate(desc.id(), context, desc);
                return Optional.of(new SkillSelection(desc.id(), decision, params, 1.0));
            }
        }

        // 2. Pattern-based Intent matching
        Matcher searchMatcher = SEARCH_PATTERN.matcher(trimmed);
        if (searchMatcher.matches()) {
            params.put("query", searchMatcher.group(1).trim());
            return buildSelection("code.search", params, context, 0.9);
        }

        Matcher inspectMatcher = INSPECT_PATTERN.matcher(trimmed);
        if (inspectMatcher.matches()) {
            params.put("symbol", inspectMatcher.group(1).trim());
            return buildSelection("code.inspect", params, context, 0.9);
        }

        Matcher traceMatcher = TRACE_PATTERN.matcher(trimmed);
        if (traceMatcher.matches()) {
            params.put("symbol", traceMatcher.group(1).trim());
            return buildSelection("code.trace", params, context, 0.9);
        }

        Matcher testMatcher = TEST_PATTERN.matcher(trimmed);
        if (testMatcher.matches()) {
            String target = testMatcher.group(1).trim();
            if (!target.isBlank()) params.put("testTarget", target);
            return buildSelection("code.test", params, context, 0.85);
        }

        Matcher explainMatcher = EXPLAIN_PATTERN.matcher(trimmed);
        if (explainMatcher.matches()) {
            params.put("symbol", explainMatcher.group(1).trim());
            return buildSelection("code.explain", params, context, 0.85);
        }

        Matcher reviewMatcher = REVIEW_PATTERN.matcher(trimmed);
        if (reviewMatcher.matches()) {
            return buildSelection("code.review", params, context, 0.85);
        }

        Matcher debugMatcher = DEBUG_PATTERN.matcher(trimmed);
        if (debugMatcher.matches()) {
            params.put("errorLog", trimmed);
            return buildSelection("code.debug", params, context, 0.8);
        }

        return Optional.empty();
    }

    private Optional<SkillSelection> buildSelection(String skillId, Map<String, Object> params, SkillContext context, double confidence) {
        var skillOpt = skillRegistry.find(skillId);
        if (skillOpt.isEmpty()) {
            return Optional.empty();
        }

        SkillDescriptor desc = skillOpt.get().descriptor();
        ReasoningDecision decision = reasoningGate.evaluate(skillId, context, desc);
        return Optional.of(new SkillSelection(skillId, decision, params, confidence));
    }
}
