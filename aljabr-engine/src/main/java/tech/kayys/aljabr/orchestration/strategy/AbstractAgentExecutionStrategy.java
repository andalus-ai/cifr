package tech.kayys.aljabr.orchestration.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.provider.InferenceRequestAdapter;
import tech.kayys.aljabr.engine.provider.ProviderRegistry;
import tech.kayys.andalus.agent.AgentRequest;
import tech.kayys.andalus.agent.AgentResponse;
import tech.kayys.andalus.agent.spi.InferenceRequest;
import tech.kayys.andalus.agent.spi.InferenceResponse;
import tech.kayys.andalus.agent.spi.InferenceTypes.AssistantMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.ChatMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.SystemMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.ToolCall;
import tech.kayys.andalus.agent.spi.InferenceTypes.ToolDefinition;
import tech.kayys.andalus.agent.spi.InferenceTypes.ToolResultMessage;
import tech.kayys.andalus.agent.spi.InferenceTypes.UserMessage;
import tech.kayys.andalus.execution.AgentDecision;
import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.inference.CompletionRequest;
import tech.kayys.andalus.inference.CompletionResult;
import tech.kayys.andalus.inference.InferenceProvider;
import tech.kayys.andalus.tool.Tool;
import tech.kayys.andalus.tool.ToolInvocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base execution strategy providing common Reason-Act-Observe loop execution.
 */
public abstract class AbstractAgentExecutionStrategy implements ExecutionStrategy {

    @Inject
    ProviderRegistry providerRegistry;

    @Inject
    jakarta.enterprise.inject.Instance<InferenceProvider> providerInstances;

    protected InferenceProvider getProvider(AgentRequest request) {
        String providerId = null;
        if (request != null && request.parameters() != null) {
            Object p = request.parameters().get("provider");
            if (p instanceof String s && !s.isBlank()) {
                providerId = s;
            }
        }
        if (providerRegistry != null) {
            if (providerId != null) {
                Optional<InferenceProvider> found = providerRegistry.find(providerId);
                if (found.isPresent()) return found.get();
            }
            if (!providerRegistry.all().isEmpty()) {
                return providerRegistry.all().get(0);
            }
        }
        if (providerInstances != null && providerInstances.isResolvable()) {
            return providerInstances.get();
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
        InferenceProvider provider = getProvider(request);
        if (provider == null) {
            return Uni.createFrom().item(AgentResponse.builder()
                    .content("No active InferenceProvider registered in runtime.")
                    .build());
        }

        InferenceRequest inferenceRequest = InferenceRequest.builder()
                .messages(messages)
                .tools(toolDefs)
                .build();

        CompletionRequest compReq = InferenceRequestAdapter.toCompletionRequest(inferenceRequest);

        return Uni.createFrom().item(() -> {
            try {
                CompletionResult compRes = provider.generate(compReq);
                return InferenceRequestAdapter.toInferenceResponse(inferenceRequest.requestId(), inferenceRequest.model(), compRes);
            } catch (Exception e) {
                throw new RuntimeException("Inference execution failed: " + e.getMessage(), e);
            }
        }).flatMap(response -> {
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
                        public tech.kayys.andalus.identity.ResourceId id() { return null; }
                        @Override
                        public tech.kayys.andalus.resource.ResourceType type() { return null; }
                        @Override
                        public tech.kayys.andalus.extension.Metadata metadata() { return null; }
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
                        
                        if (resultContent != null && resultContent.length() > 6000) {
                            resultContent = resultContent.substring(0, 6000) + "\n\n...[OUTPUT TRUNCATED BY SYSTEM TO PREVENT CONTEXT OVERFLOW]...";
                        }
                        
                        return (ChatMessage) new ToolResultMessage(call.id(), call.name(), resultContent);
                    })
                ).collect().asList();
    }

    @Override
    public Multi<AgentResponse> stream(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        InferenceProvider provider = getProvider(request);
        if (provider == null) {
            return Multi.createFrom().item(
                    AgentResponse.builder().content("No active InferenceProvider registered.").build());
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
                    // Allow up to 5 iterations of tool calls
                    int maxIterations = 5;
                    for (int i = 0; i < maxIterations && !forcedStop; i++) {
                        // Context size guard: estimate token count from message chars
                        int estimatedTokens = 0;
                        for (ChatMessage cm : messages) {
                            String text = cm.content();
                            if (text != null) estimatedTokens += text.length() / 4;
                        }
                        if (estimatedTokens > 4000) {
                            emitter.emit(AgentResponse.builder()
                                    .type("THOUGHT")
                                    .content("Context is getting large; synthesizing final answer...\n").build());
                            break;
                        }

                        InferenceRequest inferReq = InferenceRequest.builder()
                                .requestId(reqId)
                                .model(model)
                                .messages(messages)
                                .tools(toolDefs)
                                .build();

                        CompletionRequest compReq = InferenceRequestAdapter.toCompletionRequest(inferReq);
                        CompletionResult compRes = provider.generate(compReq);
                        InferenceResponse response = InferenceRequestAdapter.toInferenceResponse(reqId, model, compRes);

                        AssistantMessage assistantMsg = response != null && response.message() != null
                                ? response.message()
                                : new AssistantMessage("I have completed analyzing the workspace.", List.of());
                        messages.add(assistantMsg);

                        List<ToolCall> calls = assistantMsg.toolCalls();

                        if (calls == null || calls.isEmpty()) {
                            // Final answer — stream word by word
                            String content = assistantMsg.content() != null ? assistantMsg.content().trim() : "";
                            if (!content.isBlank()) {
                                for (String word : content.split("(?<=\\s)|(?<=\\n)")) {
                                    emitter.emit(AgentResponse.builder().type("TEXT").content(word).build());
                                }
                                hasAnswered = true;
                            }
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
                                    @Override public tech.kayys.andalus.identity.ResourceId id() { return null; }
                                    @Override public tech.kayys.andalus.resource.ResourceType type() { return null; }
                                    @Override public tech.kayys.andalus.extension.Metadata metadata() { return null; }
                                };

                                String result;
                                if (matchedTool != null) {
                                    tech.kayys.andalus.tool.ToolContext tcCtx = new tech.kayys.andalus.tool.ToolContext() {
                                        @Override public Map<String, Object> attributes() { return Map.of(); }
                                        @Override public Optional<Object> getAttribute(String key) { return Optional.empty(); }
                                    };
                                    var stage = matchedTool.execute(inv, tcCtx);
                                    var tr = stage.toCompletableFuture().join();
                                    result = mapper.writeValueAsString(tr.getOutputs());
                                } else {
                                    var dec = toolExecutor.execute(inv).toCompletableFuture().join();
                                    if (dec instanceof AgentDecision.ToolCompleted tc) {
                                        result = mapper.writeValueAsString(tc.result().getOutputs());
                                    } else {
                                        result = dec.toString();
                                    }
                                }

                                String obsDisplay = formatToolResultForUi(call.name(), result);
                                emitter.emit(AgentResponse.builder()
                                        .type("TOOL_RESULT")
                                        .content(obsDisplay).build());

                                if (result != null && result.length() > 6000) {
                                    result = result.substring(0, 6000) + "\n\n...[OUTPUT TRUNCATED BY SYSTEM TO PREVENT CONTEXT OVERFLOW]...";
                                }
                                messages.add(new ToolResultMessage(call.id(), call.name(), result));
                            } catch (Exception ex) {
                                String errObs = "Tool execution failed: " + ex.getMessage();
                                emitter.emit(AgentResponse.builder()
                                        .type("TOOL_RESULT")
                                        .content("[Error] " + errObs).build());
                                messages.add(new ToolResultMessage(call.id(), call.name(), errObs));
                            }
                        }
                    }

                    if (!hasAnswered) {
                        // Perform a final text synthesis pass without tools
                        InferenceRequest finalReq = InferenceRequest.builder()
                                .requestId(reqId + "-final")
                                .model(model)
                                .messages(messages)
                                .tools(List.of())
                                .build();
                        try {
                            CompletionRequest compReq = InferenceRequestAdapter.toCompletionRequest(finalReq);
                            CompletionResult compRes = provider.generate(compReq);
                            InferenceResponse response = InferenceRequestAdapter.toInferenceResponse(reqId, model, compRes);
                            String finalAnswer = response != null && response.message() != null ? response.message().content() : "";
                            if (finalAnswer != null && !finalAnswer.isBlank()) {
                                for (String word : finalAnswer.trim().split("(?<=\\s)|(?<=\\n)")) {
                                    emitter.emit(AgentResponse.builder().type("TEXT").content(word).build());
                                }
                                hasAnswered = true;
                            }
                        } catch (Exception ignored) {}

                        if (!hasAnswered) {
                            for (int k = messages.size() - 1; k >= 0; k--) {
                                if (messages.get(k) instanceof AssistantMessage am && am.content() != null && !am.content().isBlank()) {
                                    for (String word : am.content().trim().split("(?<=\\s)|(?<=\\n)")) {
                                        emitter.emit(AgentResponse.builder().type("TEXT").content(word).build());
                                    }
                                    hasAnswered = true;
                                    break;
                                }
                            }
                        }
                    }

                    emitter.complete();
                } catch (Exception e) {
                    emitter.emit(AgentResponse.builder()
                            .type("ERROR")
                            .content("Execution failed: " + e.getMessage()).build());
                    emitter.complete();
                }
            });
        });
    }

    private String formatToolCallForUi(String toolName, String argumentsJson) {
        try {
            Map<?, ?> args = mapper.readValue(argumentsJson, Map.class);
            switch (toolName) {
                case "read_file" -> {
                    Object path = args.get("path");
                    return "Reading `" + (path != null ? path : "file") + "`...";
                }
                case "write_file", "edit_file" -> {
                    Object path = args.get("path");
                    return "Modifying `" + (path != null ? path : "file") + "`...";
                }
                case "list_files" -> {
                    Object path = args.get("path");
                    return "Listing `" + (path != null ? path : ".") + "`...";
                }
                case "search_code", "grep" -> {
                    Object q = args.get("query") != null ? args.get("query") : args.get("pattern");
                    return "Searching for `" + (q != null ? q : "") + "`...";
                }
                case "run_command", "bash" -> {
                    Object cmd = args.get("command") != null ? args.get("command") : args.get("cmd");
                    return "Executing `" + (cmd != null ? cmd : "") + "`...";
                }
                default -> {
                    return "Calling `" + toolName + "`...";
                }
            }
        } catch (Exception ignored) {
            return "Calling `" + toolName + "`...";
        }
    }

    private String formatToolResultForUi(String toolName, String resultJson) {
        try {
            if (resultJson == null || resultJson.isBlank()) return "Done.";
            if (resultJson.length() > 500) {
                return resultJson.substring(0, 500) + "\n...(truncated)";
            }
            return resultJson;
        } catch (Exception ignored) {
            return "Done.";
        }
    }

    @Override
    public abstract String getSystemPromptFragment();
}
