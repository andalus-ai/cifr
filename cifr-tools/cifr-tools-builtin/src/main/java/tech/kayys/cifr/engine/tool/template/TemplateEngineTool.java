package tech.kayys.cifr.engine.tool.template;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class TemplateEngineTool implements CifrTool {

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");
    private static final Pattern TEMPLATE_CONDITIONAL = Pattern.compile("\\{\\{\\s*#if\\s+(\\w+)\\s*\\}\\}(.*?)\\{\\{\\s*\\/if\\s*\\}\\}", Pattern.DOTALL);

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "template",
            "Template engine with variables and conditionals rendering.",
            ToolCategory.CODE_ANALYSIS,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of(
                        "type", "string",
                        "enum", List.of("render", "register", "list"),
                        "description", "Operation: render, register, list"
                    ),
                    "template", Map.of("type", "string", "description", "Template string content"),
                    "templateName", Map.of("type", "string", "description", "Named template ID"),
                    "variables", Map.of("type", "object", "description", "Key-value variable map"),
                    "destination", Map.of("type", "string", "description", "Target file path")
                ),
                "required", List.of("action")
            ),
            Set.of(ToolCapability.READ_ONLY, ToolCapability.WRITE),
            ToolRisk.LOW
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String action = (String) request.arguments().get("action");

                return switch (action.toLowerCase()) {
                    case "render" -> renderTemplate(request, context);
                    case "register" -> registerTemplate(request);
                    case "list" -> listTemplates();
                    default -> new TemplateResult(false, "Unknown action: " + action, null);
                };
            } catch (Exception e) {
                return new TemplateResult(false, "Template operation failed: " + e.getMessage(), null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private TemplateResult renderTemplate(ToolRequest request, ToolExecutionContext context) {
        try {
            String templateContent = (String) request.arguments().get("template");
            String templateName = (String) request.arguments().get("templateName");
            Map<String, Object> variables = (Map<String, Object>) request.arguments().getOrDefault("variables", Map.of());
            String destination = (String) request.arguments().get("destination");

            String raw;
            if (templateName != null && templateCache.containsKey(templateName)) {
                raw = templateCache.get(templateName);
            } else if (templateContent != null) {
                raw = templateContent;
            } else {
                return new TemplateResult(false, "Either template content or valid templateName is required", null);
            }

            String rendered = render(raw, variables);

            if (destination != null && !destination.isBlank()) {
                Path destPath = context.resolveWorkspacePath(destination);
                if (destPath.getParent() != null) {
                    Files.createDirectories(destPath.getParent());
                }
                Files.writeString(destPath, rendered);
                return new TemplateResult(true, "Rendered to " + destination, rendered);
            }

            return new TemplateResult(true, "Template rendered successfully", rendered);

        } catch (Exception e) {
            return new TemplateResult(false, "Render error: " + e.getMessage(), null);
        }
    }

    private TemplateResult registerTemplate(ToolRequest request) {
        String name = (String) request.arguments().get("templateName");
        String content = (String) request.arguments().get("template");
        if (name == null || content == null) {
            return new TemplateResult(false, "templateName and template content required", null);
        }
        templateCache.put(name, content);
        return new TemplateResult(true, "Template registered: " + name, content);
    }

    private TemplateResult listTemplates() {
        return new TemplateResult(true, "Registered templates: " + templateCache.keySet(), null);
    }

    private String render(String template, Map<String, Object> variables) {
        // Conditionals
        Matcher condMatcher = TEMPLATE_CONDITIONAL.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (condMatcher.find()) {
            String condVar = condMatcher.group(1);
            String inner = condMatcher.group(2);
            Object val = variables.get(condVar);
            boolean isTrue = val != null && !Boolean.FALSE.equals(val) && !"false".equalsIgnoreCase(val.toString()) && !"".equals(val);
            condMatcher.appendReplacement(sb, isTrue ? Matcher.quoteReplacement(inner) : "");
        }
        condMatcher.appendTail(sb);
        String afterCond = sb.toString();

        // Variables
        Matcher varMatcher = TEMPLATE_VARIABLE.matcher(afterCond);
        StringBuffer finalSb = new StringBuffer();
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            Object val = variables.get(varName);
            String rep = val != null ? val.toString() : "";
            varMatcher.appendReplacement(finalSb, Matcher.quoteReplacement(rep));
        }
        varMatcher.appendTail(finalSb);

        return finalSb.toString();
    }
}
