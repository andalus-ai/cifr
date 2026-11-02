package tech.kayys.cifr.engine.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.andalus.agent.spi.InferenceRequest;
import tech.kayys.andalus.agent.spi.InferenceResponse;
import tech.kayys.andalus.agent.spi.InferenceTypes.AssistantMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.ChatMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.SystemMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.ToolCall;
import tech.kayys.andalus.agent.spi.InferenceTypes.ToolResultMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.TokenUsage;
import tech.kayys.andalus.agent.spi.InferenceTypes.UserMessage;
import tech.kayys.andalus.inference.Choice;
import tech.kayys.andalus.inference.CompletionRequest;
import tech.kayys.andalus.inference.CompletionResult;
import tech.kayys.andalus.inference.Message;
import tech.kayys.andalus.inference.MessageRole;
import tech.kayys.andalus.inference.Usage;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless adapter that translates between the <em>agent SPI</em>
 * ({@link InferenceRequest} / {@link InferenceResponse}) and the
 * <em>provider SPI</em> ({@link CompletionRequest} / {@link CompletionResult}).
 */
public final class InferenceRequestAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InferenceRequestAdapter() {}

    // ── Agent SPI → Provider SPI ───────────────────────────────────────────────

    public static CompletionRequest toCompletionRequest(InferenceRequest request) {
        List<Message> messages = new ArrayList<>();

        String toolInstructions = "";
        if (request.tools() != null && !request.tools().isEmpty()) {
            StringBuilder sb = new StringBuilder("\n\nYou have access to the following tools:\n");
            for (tech.kayys.andalus.agent.spi.InferenceTypes.ToolDefinition t : request.tools()) {
                String schema = "";
                try {
                    schema = MAPPER.writeValueAsString(t.parameters());
                } catch (Exception e) {
                    schema = String.valueOf(t.parameters());
                }
                sb.append("- ").append(t.name()).append(": ").append(t.description()).append("\n");
                sb.append("  Schema: ").append(schema).append("\n");
            }
            sb.append("\nTo use a tool, you MUST output a block like this:\n");
            sb.append("Action: tool_name({\"arg1\": \"value1\"})\n");
            toolInstructions = sb.toString();
        }

        if (request.messages() != null) {
            for (ChatMessage cm : request.messages()) {
                if (cm instanceof SystemMessage sm) {
                    messages.add(Message.system((sm.content() != null ? sm.content() : "") + toolInstructions));
                } else if (cm instanceof UserMessage um) {
                    messages.add(Message.user(um.content() != null ? um.content() : ""));
                } else if (cm instanceof AssistantMessage am) {
                    String content = am.content() != null ? am.content() : "";
                    if (am.toolCalls() != null && !am.toolCalls().isEmpty()) {
                        StringBuilder sb = new StringBuilder(content);
                        for (ToolCall tc : am.toolCalls()) {
                            sb.append("\n<|tool_call>call:").append(tc.name())
                              .append("(").append(tc.arguments()).append(")<tool_call|>");
                        }
                        content = sb.toString();
                    }
                    messages.add(Message.assistant(content));
                } else if (cm instanceof ToolResultMessage trm) {
                    String obs = trm.content();
                    if (obs != null && obs.length() > 3000) {
                        obs = obs.substring(0, 3000) + "\n...(truncated)";
                    }
                    messages.add(Message.tool(
                            "Observation [" + (trm.toolName() != null ? trm.toolName() : "tool") + "]:\n" + (obs != null ? obs : "{}")));
                }
            }
        }

        return CompletionRequest.builder()
                .id(request.requestId() != null ? request.requestId() : UUID.randomUUID().toString())
                .model(request.model())
                .messages(messages)
                .temperature(request.temperature() != null ? request.temperature() : 0.7)
                .maxTokens(request.maxTokens() != null ? request.maxTokens() : 2048)
                .topP(request.topP() != null ? request.topP() : 1.0)
                .stream(request.stream())
                .build();
    }

    public static InferenceRequest toInferenceRequest(CompletionRequest request) {
        List<ChatMessage> messages = new ArrayList<>();

        if (request.messages() != null) {
            for (Message m : request.messages()) {
                if (m.role() == MessageRole.SYSTEM) {
                    messages.add(new SystemMessage(m.content()));
                } else if (m.role() == MessageRole.USER) {
                    messages.add(new UserMessage(m.content()));
                } else if (m.role() == MessageRole.ASSISTANT) {
                    messages.add(new AssistantMessage(m.content(), Collections.emptyList()));
                } else if (m.role() == MessageRole.TOOL) {
                    messages.add(new ToolResultMessage(null, "tool", m.content()));
                } else {
                    messages.add(new UserMessage(m.content()));
                }
            }
        }

        return InferenceRequest.builder()
                .requestId(request.id())
                .model(request.model())
                .messages(messages)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .topP(request.topP())
                .stream(request.stream())
                .build();
    }

    // ── Provider SPI → Agent SPI ───────────────────────────────────────────────

    public static InferenceResponse toInferenceResponse(String reqId, String model, CompletionResult result) {
        String rawText = result.getContent() != null ? result.getContent() : "";
        long durationMs = result.generationTimeMs();

        TokenUsage usage = TokenUsage.of(20, 80);
        if (result.usage() != null) {
            usage = TokenUsage.of(result.usage().promptTokens(), result.usage().completionTokens());
        }

        // Check if rawText contains a tool call
        int toolCallIdx = -1;
        String json = null;

        // 1. Gemma 4 & standard tool call formats:
        //    <|tool_call>call:default_tools:list_directory{"path": "."}<tool_call|>
        //    <|tool_call>call:list_directory({"path": "."})<tool_call|>
        //    <tool_call>call:list_directory({"path": "."})</tool_call>
        //    call:list_directory({"path": "."})
        Pattern gemmaPattern = Pattern.compile(
                "(?:<\\|?/?tool_call\\|?>\\s*)?call:(?:[a-zA-Z0-9_]+:)?([a-zA-Z0-9_.-]+)\\s*\\(?\\s*(\\{.*?\\})\\s*\\)?(?:\\s*<\\|?/?tool_call\\|?>)?",
                Pattern.DOTALL);
        Matcher gm = gemmaPattern.matcher(rawText);
        if (gm.find()) {
            toolCallIdx = gm.start();
            String tName = gm.group(1);
            String tArgs = normalizeJsonArgs(gm.group(2));
            json = "{\"name\": \"" + tName + "\", \"arguments\": " + tArgs + "}";
        }

        if (json == null) {
            // Pattern for call:tool_name(path=".") or call:tool_name(path: ".")
            Pattern parenPattern = Pattern.compile(
                    "(?:<\\|?/?tool_call\\|?>\\s*)?call:(?:[a-zA-Z0-9_]+:)?([a-zA-Z0-9_.-]+)\\s*\\(([^\\)]*)\\)(?:\\s*<\\|?/?tool_call\\|?>)?",
                    Pattern.DOTALL);
            Matcher pm = parenPattern.matcher(rawText);
            if (pm.find()) {
                toolCallIdx = pm.start();
                String tName = pm.group(1);
                String tArgs = normalizeJsonArgs(pm.group(2));
                json = "{\"name\": \"" + tName + "\", \"arguments\": " + tArgs + "}";
            }
        }

        if (json == null) {
            int idx = rawText.indexOf("TOOL_CALL:");
            if (idx != -1) {
                toolCallIdx = idx;
                json = extractJsonObject(rawText.substring(idx + "TOOL_CALL:".length()));
            }
        }

        if (json == null) {
            Pattern actionPattern = Pattern.compile("Action:\\s*([a-zA-Z0-9_.-]+)[^\\{]*(\\{.*?\\})", Pattern.DOTALL);
            Matcher am = actionPattern.matcher(rawText);
            if (am.find()) {
                toolCallIdx = am.start();
                String tName = am.group(1);
                String tArgs = extractJsonObject(rawText.substring(am.start()));
                if (tArgs != null) {
                    json = "{\"name\": \"" + tName + "\", \"arguments\": " + normalizeJsonArgs(tArgs) + "}";
                }
            }
        }

        if (json == null) {
            String candidate = extractJsonObject(rawText);
            if (candidate != null) {
                try {
                    JsonNode testNode = MAPPER.readTree(candidate);
                    if (testNode.has("name") && (testNode.has("arguments") || testNode.has("parameters"))) {
                        toolCallIdx = rawText.indexOf(candidate);
                        json = candidate;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (json != null && toolCallIdx != -1) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String toolName = node.path("name").asText("");
                JsonNode argsNode = node.has("arguments") ? node.path("arguments") : node.path("parameters");
                String args = MAPPER.writeValueAsString(argsNode);
                if (!toolName.isBlank()) {
                    List<ToolCall> calls = List.of(
                            new ToolCall("call_" + toolName + "_" + System.nanoTime(), "function", toolName, args));
                    String thought = rawText.substring(0, toolCallIdx).trim()
                            .replaceAll("<\\|?channel\\|?>", "")
                            .replaceAll("(?s)<thought>.*?</thought>", "")
                            .replaceAll("(?m)^thought\\s*", "")
                            .trim();
                    return InferenceResponse.builder()
                            .responseId(reqId + "-r")
                            .requestId(reqId)
                            .model(model)
                            .message(new AssistantMessage(thought, calls))
                            .finishReason("tool_calls")
                            .usage(usage)
                            .durationMs(durationMs)
                            .build();
                }
            } catch (Exception ignored) {}
        }

        String cleanText = rawText
                .replaceAll("<\\|?channel\\|?>", "")
                .replaceAll("(?s)<thought>.*?</thought>", "")
                .replaceAll("(?m)^thought\\s*", "")
                .replaceAll("(?m)^Assistant:\\s*", "")
                .replaceAll("(?m)^FINAL_ANSWER:\\s*", "")
                .trim();

        return InferenceResponse.builder()
                .responseId(reqId + "-r")
                .requestId(reqId)
                .model(model)
                .message(new AssistantMessage(cleanText, Collections.emptyList()))
                .finishReason("stop")
                .usage(usage)
                .durationMs(durationMs)
                .build();
    }

    public static CompletionResult toCompletionResult(InferenceResponse response) {
        String text = response.message() != null ? response.message().content() : "";
        Usage usage = null;
        if (response.usage() != null) {
            usage = new Usage(response.usage().promptTokens(), response.usage().completionTokens(),
                    response.usage().totalTokens(), 0.0);
        }
        Choice choice = Choice.of(Message.assistant(text != null ? text : ""), response.finishReason());
        return new CompletionResult(
                response.requestId(),
                response.model(),
                List.of(choice),
                usage,
                Collections.emptyMap(),
                response.durationMs()
        );
    }

    private static String extractJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start == -1) return null;
        int depth = 0;
        boolean inQuotes = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (!inQuotes) {
                if (c == '{') depth++;
                else if (c == '}') {
                    if (--depth == 0) return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String normalizeJsonArgs(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                MAPPER.readTree(trimmed);
                return trimmed;
            } catch (Exception e) {
                String fixed = trimmed.replaceAll("([{\\s,])([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":")
                                      .replace('\'', '"');
                try {
                    MAPPER.readTree(fixed);
                    return fixed;
                } catch (Exception ignored) {
                    return trimmed;
                }
            }
        }

        // Handle key="val" or key: "val" or key='val'
        Pattern kvPattern = Pattern.compile("([a-zA-Z0-9_]+)\\s*[:=]\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
        Matcher matcher = kvPattern.matcher(trimmed);
        Map<String, Object> map = new LinkedHashMap<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2) != null ? matcher.group(2) :
                         matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            if ("true".equalsIgnoreCase(val)) map.put(key, true);
            else if ("false".equalsIgnoreCase(val)) map.put(key, false);
            else {
                try {
                    map.put(key, Integer.parseInt(val));
                } catch (Exception notInt) {
                    map.put(key, val);
                }
            }
        }
        if (!map.isEmpty()) {
            try {
                return MAPPER.writeValueAsString(map);
            } catch (Exception ignored) {}
        }
        return "{}";
    }
}
