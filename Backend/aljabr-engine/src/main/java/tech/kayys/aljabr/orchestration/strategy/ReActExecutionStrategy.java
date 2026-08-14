package tech.kayys.aljabr.orchestration.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.agent.spi.InferenceBackend;
import tech.kayys.wayang.agent.spi.InferenceRequest;
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

@ApplicationScoped
public class ReActExecutionStrategy implements ExecutionStrategy {

    @Inject
    InferenceBackend backend;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String id() { return "react"; }

    @Override
    public String getSystemPromptFragment() {
        return "You must operate in a strict Reason-Act-Observe loop. Always output your internal 'thought' explaining what you want to do, then emit a TOOL_CALL. After receiving an OBSERVATION from the environment, evaluate if you have reached the goal. If so, emit FINAL_ANSWER.";
    }

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
        InferenceRequest inferenceRequest = InferenceRequest.builder()
                .messages(messages)
                .tools(toolDefs)
                .build();

        return backend.infer(inferenceRequest).flatMap(response -> {
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
        return Multi.createFrom().empty();
    }
}
