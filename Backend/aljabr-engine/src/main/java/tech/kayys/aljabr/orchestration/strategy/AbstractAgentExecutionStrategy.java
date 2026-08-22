package tech.kayys.aljabr.orchestration.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.agent.spi.InferenceBackend;
import tech.kayys.wayang.agent.spi.InferenceRequest;
import tech.kayys.wayang.agent.spi.InferenceResponse;
import tech.kayys.wayang.agent.spi.InferenceTypes.AssistantMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.ChatMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.SystemMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.ToolCall;
import tech.kayys.wayang.agent.spi.InferenceTypes.ToolDefinition;
import tech.kayys.wayang.agent.spi.InferenceTypes.ToolResultMessage;
import tech.kayys.wayang.agent.spi.InferenceTypes.UserMessage;
import tech.kayys.wayang.execution.AgentDecision;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolInvocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base execution strategy providing common Reason-Act-Observe loop execution.
 */
public abstract class AbstractAgentExecutionStrategy implements ExecutionStrategy {

    @Inject
    jakarta.enterprise.inject.Instance<InferenceBackend> backendInstance;

    protected InferenceBackend getBackend() {
        if (backendInstance != null && backendInstance.isResolvable()) {
            return backendInstance.get();
        }
        return null;
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Uni<AgentResponse> execute(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(getSystemPromptFragment()));
        messages.add(new UserMessage(request.content()));

        List<ToolDefinition> toolDefs = coderSkills.stream()
                .map(tool -> new ToolDefinition(
                        tool.descriptor().name(),
                        tool.descriptor().description(),
                        tool.descriptor().inputSchema()
                )).collect(Collectors.toList());

        return loop(messages, toolDefs, request, toolExecutor);
    }

    private Uni<AgentResponse> loop(List<ChatMessage> messages, List<ToolDefinition> toolDefs, AgentRequest request, AgentToolExecutor toolExecutor) {
        InferenceBackend b = getBackend();
        if (b == null) {
            return Uni.createFrom().item(AgentResponse.builder()
                    .content("No active InferenceBackend registered in runtime.")
                    .build());
        }

        InferenceRequest inferenceRequest = InferenceRequest.builder()
                .messages(messages)
                .tools(toolDefs)
                .build();

        return b.infer(inferenceRequest).flatMap(response -> {
            AssistantMessage msg = response.message();
            messages.add(msg);

            if (msg.toolCalls() == null || msg.toolCalls().isEmpty()) {
                return Uni.createFrom().item(AgentResponse.builder()
                        .content(msg.content())
                        .build());
            }

            return executeTools(msg.toolCalls(), toolExecutor).flatMap(toolResults -> {
                messages.addAll(toolResults);
                return loop(messages, toolDefs, request, toolExecutor);
            });
        });
    }

    private Uni<List<ChatMessage>> executeTools(List<ToolCall> calls, AgentToolExecutor toolExecutor) {
        return Multi.createFrom().iterable(calls)
                .onItem().transformToUniAndConcatenate(call ->
                    Uni.createFrom().completionStage(toolExecutor.execute(new ToolInvocation() {
                        @Override
                        public String name() {
                            return call.name();
                        }
                        @Override
                        public Map<String, Object> arguments() {
                            try {
                                return mapper.readValue(call.arguments(), Map.class);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        @Override
                        public tech.kayys.wayang.identity.ResourceId id() { return null; }
                        @Override
                        public tech.kayys.wayang.resource.ResourceType type() { return null; }
                        @Override
                        public tech.kayys.wayang.extension.Metadata metadata() { return null; }
                    })).map(decision -> {
                        String resultContent = "";
                        if (decision instanceof AgentDecision.ToolCompleted tc) {
                            try {
                                resultContent = mapper.writeValueAsString(tc.result().getOutputs());
                            } catch (JsonProcessingException e) {
                                resultContent = "Error mapping result";
                            }
                        } else {
                            resultContent = decision.toString();
                        }
                        return (ChatMessage) new ToolResultMessage(call.id(), call.name(), resultContent);
                    })
                ).collect().asList();
    }

    @Override
    public Multi<AgentResponse> stream(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        InferenceBackend backend = getBackend();
        if (backend == null) {
            return Multi.createFrom().item(
                    AgentResponse.builder().content("No active InferenceBackend registered.").build());
        }

        // Extract workspace path from request parameters (set by the frontend's selected project)
        String workspacePath = (request.parameters() != null && request.parameters().containsKey("workspace_path"))
                ? (String) request.parameters().get("workspace_path")
                : null;

        String systemPrompt = getSystemPromptFragment();
        if (workspacePath != null && !workspacePath.isBlank()) {
            systemPrompt = systemPrompt + "\nCurrent project workspace: " + workspacePath
                    + "\nAll file paths you use with tools are relative to this workspace root.";
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(request.content() != null ? request.content() : ""));

        List<ToolDefinition> toolDefs = coderSkills.stream()
                .map(tool -> new ToolDefinition(
                        tool.descriptor().name(),
                        tool.descriptor().description(),
                        tool.descriptor().inputSchema()))
                .collect(Collectors.toList());

        String model = (request.parameters() != null && request.parameters().containsKey("model"))
                ? (String) request.parameters().get("model")
                : "gemini-2.5-flash";

        String reqId = request.id() != null ? String.valueOf(request.id()) : java.util.UUID.randomUUID().toString();

        // ReAct loop: runs on worker thread pool to avoid blocking the Vert.x eventloop thread
        return Multi.createFrom().emitter(emitter -> {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    boolean hasAnswered = false;
                    java.util.Set<String> seenCalls = new java.util.HashSet<>();
                    boolean forcedStop = false;
                    for (int i = 0; i < 8 && !forcedStop; i++) {
                        InferenceRequest inferReq = InferenceRequest.builder()
                                .requestId(reqId)
                                .model(model)
                                .messages(messages)
                                .tools(toolDefs)
                                .build();

                        InferenceResponse response = backend.infer(inferReq).await().indefinitely();
                        AssistantMessage assistantMsg = response.message();
                        messages.add(assistantMsg);

                        List<ToolCall> calls = assistantMsg.toolCalls();

                        if (calls == null || calls.isEmpty()) {
                            // Final answer — stream word by word
                            String content = assistantMsg.content() != null ? assistantMsg.content() : "";
                            for (String word : content.split("(?<=\\s)|(?<=\\n)")) {
                                emitter.emit(AgentResponse.builder().type("TEXT").content(word).build());
                            }
                            hasAnswered = true;
                            break;
                        }

                        // Stream thinking text if present
                        if (assistantMsg.content() != null && !assistantMsg.content().isBlank()) {
                            emitter.emit(AgentResponse.builder()
                                    .type("THOUGHT")
                                    .content(assistantMsg.content() + "\n").build());
                        }

                        // Execute each tool call and stream the observation back
                        for (ToolCall call : calls) {
                            // Duplicate call guard — prevent infinite loops
                            String callKey = call.name() + "::" + call.arguments();
                            if (seenCalls.contains(callKey)) {
                                forcedStop = true;
                                break;
                            }
                            seenCalls.add(callKey);

                            String callDisplay = formatToolCallForUi(call.name(), call.arguments());
                            emitter.emit(AgentResponse.builder()
                                    .type("TOOL_CALL")
                                    .content(callDisplay).build());
                            try {
                                Tool matchedTool = null;
                                for (Tool t : coderSkills) {
                                    if (t.descriptor() != null && call.name().equals(t.descriptor().name())) {
                                        matchedTool = t;
                                        break;
                                    }
                                }

                                ToolInvocation inv = new ToolInvocation() {
                                    @Override public String name() { return call.name(); }
                                    @Override public Map<String, Object> arguments() {
                                        try { return mapper.readValue(call.arguments(), Map.class); }
                                        catch (Exception ex) { return Map.of(); }
                                    }
                                    @Override public tech.kayys.wayang.identity.ResourceId id() { return null; }
                                    @Override public tech.kayys.wayang.resource.ResourceType type() { return null; }
                                    @Override public tech.kayys.wayang.extension.Metadata metadata() { return null; }
                                };

                                String result;
                                String observationForModel;
                                String displayForUi;

                                if (matchedTool != null) {
                                    // Set session workspace so all tools resolve paths against the correct project
                                    if (workspacePath != null) {
                                        tech.kayys.aljabr.engine.tool.WorkspaceResolver.setSessionWorkspace(workspacePath);
                                    }
                                    tech.kayys.wayang.tool.ToolResult tr;
                                    try {
                                        tr = matchedTool.execute(inv, null).get();
                                    } finally {
                                        tech.kayys.aljabr.engine.tool.WorkspaceResolver.clearSessionWorkspace();
                                    }
                                    result = tr != null ? mapper.writeValueAsString(tr.getOutputs()) : "{}";
                                    if (tr != null && tr.getOutputs() != null) {
                                        Object summary = tr.getOutputs().get("summary");
                                        Object entries = tr.getOutputs().get("entries");
                                        Object fileContent = tr.getOutputs().get("content");
                                        Object matches = tr.getOutputs().get("matches");
                                        Object path = tr.getOutputs().get("path");

                                        if (fileContent != null) {
                                            observationForModel = "File (" + (path != null ? path : "content") + "):\n```\n" + fileContent + "\n```";
                                            displayForUi = summary != null ? summary.toString() : "Read file " + path;
                                        } else if (entries != null) {
                                            observationForModel = (summary != null ? summary : "Directory listing") + ": " + entries;
                                            displayForUi = observationForModel;
                                        } else if (matches != null) {
                                            observationForModel = (summary != null ? summary : "Search results") + ": " + matches;
                                            displayForUi = observationForModel;
                                        } else if (summary != null) {
                                            observationForModel = summary.toString();
                                            displayForUi = summary.toString();
                                        } else {
                                            observationForModel = result;
                                            displayForUi = result;
                                        }
                                    } else {
                                        observationForModel = result;
                                        displayForUi = result;
                                    }
                                } else {
                                    AgentDecision decision = toolExecutor.execute(inv).toCompletableFuture().get();
                                    if (decision instanceof AgentDecision.ToolCompleted tc) {
                                        result = mapper.writeValueAsString(tc.result().getOutputs());
                                        Object summary = tc.result().getOutputs().get("summary");
                                        Object fileContent = tc.result().getOutputs().get("content");
                                        if (fileContent != null) {
                                            observationForModel = "Content:\n```\n" + fileContent + "\n```";
                                            displayForUi = summary != null ? summary.toString() : result;
                                        } else {
                                            displayForUi = summary != null ? summary.toString() : result;
                                            observationForModel = displayForUi;
                                        }
                                    } else {
                                        result = decision.toString();
                                        displayForUi = result;
                                        observationForModel = result;
                                    }
                                }
                                messages.add(new ToolResultMessage(call.id(), call.name(), observationForModel));
                                String resultDisplay = formatToolResultForUi(call.name(), displayForUi);
                                emitter.emit(AgentResponse.builder().type("TOOL_RESULT").content(resultDisplay).build());
                            } catch (Exception ex) {
                                String errMsg = "Error: " + ex.getMessage();
                                messages.add(new ToolResultMessage(call.id(), call.name(), errMsg));
                                String errDisplay = "> ⚠️ *" + errMsg + "*\n\n";
                                emitter.emit(AgentResponse.builder()
                                        .type("TOOL_RESULT")
                                        .content(errDisplay).build());
                            }
                        }
                    }

                    // If loop finished without a final text answer, do a synthesis step
                    if (!hasAnswered) {
                        messages.add(new UserMessage("Based on the observations above, provide a comprehensive final answer to the user's request."));
                        InferenceRequest finalReq = InferenceRequest.builder()
                                .requestId(reqId)
                                .model(model)
                                .messages(messages)
                                .tools(List.of())
                                .build();
                        InferenceResponse finalResp = backend.infer(finalReq).await().indefinitely();
                        String finalContent = finalResp.message() != null && finalResp.message().content() != null
                                ? finalResp.message().content()
                                : "";
                        for (String word : finalContent.split("(?<=\\s)|(?<=\\n)")) {
                            emitter.emit(AgentResponse.builder().type("TEXT").content(word).build());
                        }
                    }
                } catch (Exception e) {
                    emitter.emit(AgentResponse.builder()
                            .type("ERROR")
                            .content("Agent error: " + e.getMessage()).build());
                } finally {
                    emitter.complete();
                }
            });
        });
    }

    private String formatToolCallForUi(String toolName, String argumentsJson) {
        String icon = getToolIcon(toolName);
        try {
            if (argumentsJson != null && !argumentsJson.isBlank()) {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(argumentsJson);
                if (node.isObject() && node.size() == 1) {
                    String singleKey = node.fieldNames().next();
                    String singleVal = node.get(singleKey).asText();
                    return "\n> " + icon + " **`" + toolName + "`** `" + singleKey + ": " + singleVal + "`\n";
                }
            }
        } catch (Exception ignored) {}
        return "\n> " + icon + " **`" + toolName + "`** " + (argumentsJson != null && !argumentsJson.equals("{}") ? "`" + argumentsJson + "`" : "") + "\n";
    }

    private String formatToolResultForUi(String toolName, String displayResult) {
        return "> ↳ *" + displayResult + "*\n\n";
    }

    private String getToolIcon(String toolName) {
        if (toolName == null) return "⚙️";
        String lower = toolName.toLowerCase();
        if (lower.contains("directory") || lower.contains("folder")) return "📁";
        if (lower.contains("read") || lower.contains("file")) return "📄";
        if (lower.contains("write") || lower.contains("patch") || lower.contains("edit")) return "✏️";
        if (lower.contains("find") || lower.contains("search")) return "🔍";
        if (lower.contains("git")) return "🌿";
        if (lower.contains("build") || lower.contains("test")) return "🔨";
        return "⚙️";
    }
}
