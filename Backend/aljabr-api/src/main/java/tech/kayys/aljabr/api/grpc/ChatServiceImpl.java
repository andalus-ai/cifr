package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.wayang.pro.api.grpc.*;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.aljabr.orchestration.CoderOrchestrator;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.time.Duration;
import java.util.logging.Logger;

@GrpcService
public class ChatServiceImpl implements ChatService {

    private static final Logger log = Logger.getLogger(ChatServiceImpl.class.getName());

    @Inject
    CoderOrchestrator orchestrator;

    private final Map<String, List<ChatMessage>> sessionMessages = new ConcurrentHashMap<>();

    @Override
    public Uni<ListChatMessagesResponse> listMessages(ListChatMessagesRequest request) {
        List<ChatMessage> messages = sessionMessages.getOrDefault(request.getSessionId(), new ArrayList<>());
        return Uni.createFrom().item(ListChatMessagesResponse.newBuilder()
                .addAllMessages(messages)
                .build());
    }

    @Override
    public Uni<ChatMessage> addMessage(AddChatMessageRequest request) {
        ChatMessage msg = ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSessionId(request.getSessionId())
                .setRole(request.getRole())
                .setContent(request.getContent())
                .setCreatedAt(Instant.now().toString())
                .build();
        sessionMessages.computeIfAbsent(request.getSessionId(), k -> new ArrayList<>()).add(msg);
        return Uni.createFrom().item(msg);
    }

    @Override
    public Multi<ChatMessage> observeSession(ObserveSessionRequest request) {
        // Return empty stream as acknowledgment
        return Multi.createFrom().empty();
    }

    @Override
    public Multi<RunAgentEvent> runAgent(RunAgentRequest request) {
        log.info("Received runAgent request for session: " + request.getSessionId());
        
        AgentRequest agentReq = AgentRequest.builder()
                .sessionId(request.getSessionId())
                .content(request.getPrompt())
                .build();
                
        return orchestrator.stream(agentReq)
                .map(response -> {
                    String text = response.content() != null ? response.content() : "";
                    return RunAgentEvent.newBuilder()
                            .setType("TEXT")
                            .setContent(text)
                            .build();
                })
                .onCompletion().continueWith(
                        RunAgentEvent.newBuilder().setType("DONE").setContent("Completed").build()
                );
    }
}
