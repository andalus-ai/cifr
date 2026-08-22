package tech.kayys.aljabr.engine.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.wayang.agent.spi.BackendCapabilities;
import tech.kayys.wayang.agent.spi.InferenceBackend;
import tech.kayys.wayang.agent.spi.InferenceRequest;
import tech.kayys.wayang.agent.spi.InferenceResponse;
import tech.kayys.wayang.agent.spi.InferenceTypes.AssistantMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.ChatMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.ProviderInfo;
import tech.kayys.wayang.agent.spi.InferenceTypes.StreamingChunk;
import tech.kayys.wayang.agent.spi.InferenceTypes.SystemMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.TokenUsage;
import tech.kayys.wayang.agent.spi.InferenceTypes.ToolCall;
import tech.kayys.wayang.agent.spi.InferenceTypes.ToolDefinition;
import tech.kayys.wayang.agent.spi.InferenceTypes.ToolResultMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.UserMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InferenceBackend connecting to:
 *  1. Local Gollek Native GGUF Engine (via `gollek run -m <model> -q -p ...`)
 *  2. Local HTTP endpoints (Gollek API / Ollama at :8080 / :11434)
 *  3. Cloud Gemini 2.5 Flash as resilient fallback
 */
@ApplicationScoped
public class DefaultInferenceBackend implements InferenceBackend {

    private static final Logger LOG = Logger.getLogger(DefaultInferenceBackend.class.getName());
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("TOOL_CALL:\\s*(\\{.*?\\})", Pattern.DOTALL);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String name() { return "gollek"; }

    @Override
    public String version() { return "0.1.0"; }

    @Override
    public Uni<InferenceResponse> infer(InferenceRequest request) {
        String reqId = request.requestId() != null ? request.requestId() : UUID.randomUUID().toString();
        String model = resolveModel(request);

        return Uni.createFrom().item(() -> {
            // 1. Try local native Gollek CLI runner (e.g. gemma-4-12b-it-GGUF)
            InferenceResponse localGollekResp = tryLocalGollekCliInference(reqId, model, request);
            if (localGollekResp != null) {
                return localGollekResp;
            }

            // 2. Try Local HTTP OpenAI-compatible endpoint (:8080, :11434)
            String localHttpResp = tryLocalOpenAiInference(request, model);
            if (localHttpResp != null && !localHttpResp.isBlank()) {
                return buildTextResponse(reqId, model, localHttpResp);
            }

            // 3. Fallback to Gemini Cloud API
            return tryGeminiInference(reqId, model, request);
        });
    }

    @Override
    public Multi<StreamingChunk> stream(InferenceRequest request) {
        String reqId = request.requestId() != null ? request.requestId() : UUID.randomUUID().toString();
        String model = resolveModel(request);

        return Multi.createFrom().emitter(emitter -> {
            try {
                // 1. Try local Gollek CLI runner
                InferenceResponse localGollekResp = tryLocalGollekCliInference(reqId, model, request);
                if (localGollekResp != null) {
                    String text = localGollekResp.message() != null ? localGollekResp.message().content() : "";
                    if (text != null && !text.isBlank()) {
                        streamWords(emitter, reqId, text);
                        return;
                    }
                }

                // 2. Try local HTTP endpoints
                String localResp = tryLocalOpenAiInference(request, model);
                if (localResp != null && !localResp.isBlank()) {
                    streamWords(emitter, reqId, localResp);
                    return;
                }

                // 3. Fallback: Gemini SSE stream
                boolean streamed = streamFromGemini(emitter, reqId, model, request);
                if (!streamed) {
                    streamWords(emitter, reqId, "Ready. How can I assist you?");
                }
            } catch (Exception e) {
                LOG.warning("Streaming error: " + e.getMessage());
                emitter.emit(new StreamingChunk(reqId, "Error: " + e.getMessage(), Collections.emptyList(), "stop", TokenUsage.of(0, 0)));
                emitter.complete();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Local Gollek Native GGUF Runner (llama.cpp Metal daemon)
    // -------------------------------------------------------------------------

    private InferenceResponse tryLocalGollekCliInference(String reqId, String rawModel, InferenceRequest request) {
        String gollekBin = findGollekBinary();
        if (gollekBin == null) {
            return null;
        }

        String targetModel = resolveGollekModelIdentifier(rawModel);

        StringBuilder promptBuilder = new StringBuilder();
        String workspacePath = resolveWorkspacePath();
        String systemPrompt = "You are Aljabr, an autonomous coding agent. Current workspace: " + workspacePath + ". You can inspect code, run tools, and answer questions.";

        if (request.messages() != null) {
            for (ChatMessage m : request.messages()) {
                if (m instanceof SystemMessage sm) {
                    systemPrompt = sm.content() + "\nCurrent workspace: " + workspacePath;
                } else if (m instanceof UserMessage um) {
                    promptBuilder.append("User: ").append(um.content()).append("\n");
                } else if (m instanceof AssistantMessage am) {
                    if (am.content() != null && !am.content().isBlank()) {
                        promptBuilder.append("Assistant: ").append(am.content()).append("\n");
                    }
                    if (am.toolCalls() != null) {
                        for (ToolCall tc : am.toolCalls()) {
                            promptBuilder.append("Action: ").append(tc.name()).append("(").append(tc.arguments()).append(")\n");
                        }
                    }
                } else if (m instanceof ToolResultMessage trm) {
                    // Truncate large tool observations to avoid infinite loops from context overflow
                    String obs = trm.content();
                    if (obs != null && obs.length() > 6000) {
                        int cutoff = obs.indexOf('\n', 6000);
                        obs = obs.substring(0, cutoff > 0 ? cutoff : 6000) + "\n... (truncated)";
                    }
                    promptBuilder.append("Observation [").append(trm.toolName()).append("]: ").append(obs).append("\n");
                }
            }
        }

        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append("System: ").append(systemPrompt).append("\n\n");

        if (request.tools() != null && !request.tools().isEmpty()) {
            fullPrompt.append("Available Workspace Tools:\n");
            for (ToolDefinition td : request.tools()) {
                fullPrompt.append("- ").append(td.name()).append(": ").append(td.description()).append("\n");
            }
            fullPrompt.append("\nINSTRUCTION: Use tools only when needed. Once you have enough information, stop calling tools and respond directly with your answer.\n");
            fullPrompt.append("To call a tool output ONLY: TOOL_CALL: {\"name\": \"<tool_name>\", \"arguments\": {<json_arguments>}}\n\n");
        }

        fullPrompt.append(promptBuilder);
        if (!promptBuilder.toString().trim().endsWith("Assistant:")) {
            fullPrompt.append("Assistant:\n");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    gollekBin, "run", "-m", targetModel, "-q", "-p", fullPrompt.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String rawText = extractGeneratedText(output.toString());
                if (rawText != null && !rawText.isBlank()) {
                    int toolCallIdx = -1;
                    String json = null;

                    // Pattern 1: TOOL_CALL: {...}
                    int idx = rawText.indexOf("TOOL_CALL:");
                    if (idx != -1) {
                        toolCallIdx = idx;
                        json = extractJsonObject(rawText.substring(idx + "TOOL_CALL:".length()));
                    }

                    // Pattern 2: Action: tool_name({...})
                    if (json == null) {
                        Pattern actionPattern = Pattern.compile("Action:\\s*([a-zA-Z0-9_.-]+)\\s*(\\{.*?\\})", Pattern.DOTALL);
                        Matcher am = actionPattern.matcher(rawText);
                        if (am.find()) {
                            toolCallIdx = am.start();
                            String tName = am.group(1);
                            String tArgs = extractJsonObject(rawText.substring(am.start()));
                            if (tArgs != null) {
                                json = "{\"name\": \"" + tName + "\", \"arguments\": " + tArgs + "}";
                            }
                        }
                    }

                    // Pattern 3: Standalone JSON object with "name" and "arguments"
                    if (json == null) {
                        String candidate = extractJsonObject(rawText);
                        if (candidate != null) {
                            try {
                                JsonNode testNode = mapper.readTree(candidate);
                                if (testNode.has("name") && (testNode.has("arguments") || testNode.has("parameters"))) {
                                    toolCallIdx = rawText.indexOf(candidate);
                                    json = candidate;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (json != null && toolCallIdx != -1) {
                        try {
                            JsonNode node = mapper.readTree(json);
                            String toolName = node.path("name").asText("");
                            JsonNode argsNode = node.has("arguments") ? node.path("arguments") : node.path("parameters");
                            String args = mapper.writeValueAsString(argsNode);
                            if (!toolName.isBlank()) {
                                List<ToolCall> calls = List.of(new ToolCall("call_" + toolName + "_" + System.nanoTime(), "function", toolName, args));
                                String thought = rawText.substring(0, toolCallIdx).trim();
                                thought = thought.replaceAll("<\\|?channel\\|?>", "")
                                                 .replaceAll("(?s)<thought>.*?</thought>", "")
                                                 .replaceAll("(?m)^thought\\s*", "")
                                                 .trim();
                                return buildToolCallResponse(reqId, rawModel, thought, calls);
                            }
                        } catch (Exception ex) {
                            LOG.warning("Failed to parse TOOL_CALL JSON: " + ex.getMessage());
                        }
                    }

                    String cleanText = rawText
                            .replaceAll("<\\|?channel\\|?>", "")
                            .replaceAll("(?s)<thought>.*?</thought>", "")
                            .replaceAll("(?m)^thought\\s*", "")
                            .replaceAll("(?m)^Assistant:\\s*", "")
                            .replaceAll("(?m)^FINAL_ANSWER:\\s*", "")
                            .trim();
                    return buildTextResponse(reqId, rawModel, cleanText);
                }
            }
        } catch (Exception e) {
            LOG.warning("Local Gollek CLI execution failed: " + e.getMessage());
        }
        return null;
    }

    private String extractGeneratedText(String rawOutput) {
        // Output from gollek run contains header, daemon line, generated text, and footer metrics
        String[] lines = rawOutput.split("\n");
        StringBuilder genText = new StringBuilder();
        boolean recording = false;

        for (String line : lines) {
            if (line.startsWith("Using llama.cpp GGUF daemon") || line.startsWith("--------------------------------------------------")) {
                recording = true;
                continue;
            }
            if (line.startsWith("[Fast GGUF") || line.startsWith("Performance Metrics:")) {
                break;
            }
            if (recording) {
                genText.append(line).append("\n");
            }
        }

        if (genText.length() == 0) {
            // Fallback: strip banner if present
            return rawOutput.replaceAll("(?s).*?\\\\_\\\\_", "").trim();
        }
        return genText.toString().trim();
    }

    private String resolveGollekModelIdentifier(String model) {
        if (model == null || model.isBlank()) return "c961e2";
        if (model.contains("gemma-4-12b")) return "c961e2";
        if (model.contains("gemma-4-26B") || model.contains("gemma-4-26b")) return "98714b";
        if (model.startsWith("hf:")) return model.substring(3);
        return model;
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
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private String findGollekBinary() {
        for (String path : List.of("/Users/bhangun/.local/bin/gollek", "/usr/local/bin/gollek", "gollek")) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) return path;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Cloud / OpenAI Fallback Helpers
    // -------------------------------------------------------------------------

    private String resolveModel(InferenceRequest request) {
        return (request.model() != null && !request.model().isBlank()) ? request.model() : "hf:unsloth/gemma-4-12b-it-GGUF";
    }

    private InferenceResponse buildTextResponse(String reqId, String model, String text) {
        return InferenceResponse.builder()
                .responseId(reqId + "-r")
                .requestId(reqId)
                .model(model)
                .message(new AssistantMessage(text, Collections.emptyList()))
                .finishReason("stop")
                .usage(TokenUsage.of(20, 80))
                .durationMs(120)
                .build();
    }

    private InferenceResponse buildToolCallResponse(String reqId, String model, String thinkingText, List<ToolCall> calls) {
        return InferenceResponse.builder()
                .responseId(reqId + "-r")
                .requestId(reqId)
                .model(model)
                .message(new AssistantMessage(thinkingText, calls))
                .finishReason("tool_calls")
                .usage(TokenUsage.of(20, 40))
                .durationMs(150)
                .build();
    }

    private String tryLocalOpenAiInference(InferenceRequest request, String model) {
        for (String endpoint : List.of(
                "http://localhost:8080/v1/chat/completions",
                "http://localhost:11434/v1/chat/completions")) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model);
                body.put("stream", false);

                List<Map<String, String>> msgs = new ArrayList<>();
                if (request.messages() != null) {
                    for (ChatMessage m : request.messages()) {
                        String role = (m instanceof SystemMessage) ? "system" : (m instanceof AssistantMessage) ? "assistant" : "user";
                        msgs.add(Map.of("role", role, "content", m.content() != null ? m.content() : ""));
                    }
                }
                body.put("messages", msgs);

                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer community")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .timeout(Duration.ofSeconds(2))
                        .build();

                HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String content = mapper.readTree(resp.body()).path("choices").path(0).path("message").path("content").asText("");
                    if (!content.isBlank()) return content;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private InferenceResponse tryGeminiInference(String reqId, String model, InferenceRequest request) {
        String apiKey = getGeminiApiKey();
        if (apiKey == null) return buildTextResponse(reqId, model, "No LLM provider configured. Set GEMINI_API_KEY.");

        try {
            Map<String, Object> body = buildGeminiBody(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOG.warning("Gemini error " + resp.statusCode() + ": " + resp.body());
                return buildTextResponse(reqId, model, "Gemini error " + resp.statusCode());
            }
            return parseGeminiResponse(reqId, model, mapper.readTree(resp.body()));
        } catch (Exception e) {
            LOG.warning("Gemini error: " + e.getMessage());
            return buildTextResponse(reqId, model, "Inference error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildGeminiBody(InferenceRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();
        String systemPrompt = null;

        if (request.messages() != null) {
            for (ChatMessage m : request.messages()) {
                if (m instanceof SystemMessage sm) {
                    systemPrompt = sm.content();
                } else if (m instanceof AssistantMessage am) {
                    List<Map<String, Object>> parts = new ArrayList<>();
                    if (am.content() != null && !am.content().isBlank()) {
                        parts.add(Map.of("text", am.content()));
                    }
                    if (am.toolCalls() != null) {
                        for (ToolCall tc : am.toolCalls()) {
                            Map<String, Object> argsMap = Map.of();
                            try {
                                argsMap = mapper.readValue(tc.arguments(), Map.class);
                            } catch (Exception ignored) {}
                            parts.add(Map.of("functionCall", Map.of(
                                    "name", tc.name(),
                                    "args", argsMap
                            )));
                        }
                    }
                    if (!parts.isEmpty()) {
                        contents.add(Map.of("role", "model", "parts", parts));
                    }
                } else if (m instanceof ToolResultMessage trm) {
                    contents.add(Map.of(
                            "role", "function",
                            "parts", List.of(Map.of(
                                    "functionResponse", Map.of(
                                            "name", trm.toolName() != null ? trm.toolName() : "tool",
                                            "response", Map.of("output", trm.content() != null ? trm.content() : "")
                                    )
                            ))
                    ));
                } else {
                    String text = m.content() != null ? m.content() : "";
                    contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", text))));
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", contents);

        if (request.tools() != null && !request.tools().isEmpty()) {
            List<Map<String, Object>> functionDeclarations = new ArrayList<>();
            for (ToolDefinition td : request.tools()) {
                Map<String, Object> decl = new LinkedHashMap<>();
                decl.put("name", td.name());
                decl.put("description", td.description() != null ? td.description() : td.name());
                if (td.parameters() != null && !td.parameters().isEmpty()) {
                    decl.put("parameters", sanitizeSchema(td.parameters()));
                }
                functionDeclarations.add(decl);
            }
            body.put("tools", List.of(Map.of("function_declarations", functionDeclarations)));
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeSchema(Map<String, Object> schema) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if ("type".equalsIgnoreCase(key) && val instanceof String typeStr) {
                sanitized.put("type", typeStr.toUpperCase(Locale.ROOT));
            } else if (val instanceof Map<?, ?> mapVal) {
                sanitized.put(key, sanitizeSchema((Map<String, Object>) mapVal));
            } else {
                sanitized.put(key, val);
            }
        }
        if (!sanitized.containsKey("type")) {
            sanitized.put("type", "OBJECT");
        }
        return sanitized;
    }

    private InferenceResponse parseGeminiResponse(String reqId, String model, JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.size() == 0) return buildTextResponse(reqId, model, "");

        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        for (JsonNode part : parts) {
            if (part.has("functionCall")) {
                JsonNode fc = part.path("functionCall");
                String name = fc.path("name").asText();
                String argsJson = "{}";
                try { argsJson = mapper.writeValueAsString(fc.path("args")); } catch (Exception ignored) {}
                toolCalls.add(new ToolCall("call_" + name + "_" + System.nanoTime(), "function", name, argsJson));
            } else if (part.has("text")) {
                text.append(part.path("text").asText());
            }
        }

        return toolCalls.isEmpty()
                ? buildTextResponse(reqId, model, text.toString())
                : buildToolCallResponse(reqId, model, text.toString(), toolCalls);
    }

    private boolean streamFromGemini(io.smallrye.mutiny.subscription.MultiEmitter<? super StreamingChunk> emitter,
                                      String reqId, String model, InferenceRequest request) {
        String apiKey = getGeminiApiKey();
        if (apiKey == null) return false;
        try {
            Map<String, Object> body = buildGeminiBody(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=" + apiKey + "&alt=sse"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<java.io.InputStream> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) return false;

            boolean received = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String json = line.substring(6).trim();
                        if (!json.isEmpty()) {
                            try {
                                JsonNode root = mapper.readTree(json);
                                for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                                    String chunk = part.path("text").asText("");
                                    if (!chunk.isEmpty()) {
                                        emitter.emit(new StreamingChunk(reqId, chunk, Collections.emptyList(), null, null));
                                        received = true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (received) {
                emitter.emit(new StreamingChunk(reqId, "", Collections.emptyList(), "stop", TokenUsage.of(15, 60)));
                emitter.complete();
                return true;
            }
        } catch (Exception e) {
            LOG.warning("Gemini stream error: " + e.getMessage());
        }
        return false;
    }

    private void streamWords(io.smallrye.mutiny.subscription.MultiEmitter<? super StreamingChunk> emitter,
                              String reqId, String text) {
        for (String word : text.split("(?<=\\s)|(?<=\\n)")) {
            emitter.emit(new StreamingChunk(reqId, word, Collections.emptyList(), null, null));
        }
        emitter.emit(new StreamingChunk(reqId, "", Collections.emptyList(), "stop", TokenUsage.of(10, 50)));
        emitter.complete();
    }

    private String getGeminiApiKey() {
        for (String env : new String[]{"GEMINI_API_KEY", "GOOGLE_API_KEY", "API_KEY"}) {
            String v = System.getenv(env);
            if (v != null && !v.isBlank()) return v;
        }
        return System.getProperty("gemini.api.key");
    }

    @Override public List<ProviderInfo> listProviders() { return Collections.emptyList(); }
    @Override public boolean isHealthy() { return true; }
    @Override public BackendCapabilities capabilitiesInfo() { return BackendCapabilities.none(); }

    private String resolveWorkspacePath() {
        String env = System.getenv("WORKSPACE_ROOT");
        if (env != null && !env.isBlank()) {
            java.nio.file.Path p = java.nio.file.Path.of(env).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(p)) return p.toString();
        }
        String prop = System.getProperty("workspace.root");
        if (prop != null && !prop.isBlank()) {
            java.nio.file.Path p = java.nio.file.Path.of(prop).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(p)) return p.toString();
        }
        java.nio.file.Path curr = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        java.nio.file.Path check = curr;
        while (check != null && check.getParent() != null) {
            if (check.getFileName() != null && check.getFileName().toString().equals("wayang-platform")) {
                return check.toString();
            }
            check = check.getParent();
        }
        return curr.toString();
    }
}
