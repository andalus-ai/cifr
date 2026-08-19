package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.wayang.pro.api.grpc.*;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.aljabr.orchestration.CoderOrchestrator;
import tech.kayys.aljabr.api.entity.ChatMessageEntity;
import tech.kayys.aljabr.api.repository.ChatMessageRepository;
import tech.kayys.aljabr.engine.event.DefaultEventLedger;
import tech.kayys.aljabr.api.agent.AgentEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.logging.Logger;

@GrpcService
public class ChatServiceImpl implements ChatService {

    private static final Logger log = Logger.getLogger(ChatServiceImpl.class.getName());

    @Inject
    CoderOrchestrator orchestrator;

    @Inject
    ChatMessageRepository messageRepo;

    @Inject
    DefaultEventLedger eventLedger;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // In-memory fallback message storage per session
    private final Map<String, List<ChatMessageEntity>> memoryMessages = new ConcurrentHashMap<>();

    @Override
    public Uni<ListChatMessagesResponse> listMessages(ListChatMessagesRequest request) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default-session";
        List<ChatMessageEntity> list = memoryMessages.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        List<ChatMessage> messages = list.stream()
            .map(this::toChatMessageProto)
            .collect(Collectors.toList());
        return Uni.createFrom().item(ListChatMessagesResponse.newBuilder().addAllMessages(messages).build());
    }

    @Override
    public Uni<ChatMessage> addMessage(AddChatMessageRequest request) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.id = UUID.randomUUID().toString();
        msg.sessionId = request.getSessionId() != null && !request.getSessionId().isBlank() ? request.getSessionId() : "default-session";
        msg.role = request.getRole() != null ? request.getRole() : "USER";
        msg.content = request.getContent() != null ? request.getContent() : "";
        msg.toolCalls = request.getToolCalls() != null ? request.getToolCalls() : "";
        msg.tokens = request.getTokens();
        msg.model = request.getModel() != null ? request.getModel() : "";
        msg.createdAt = Instant.now().toString();

        memoryMessages.computeIfAbsent(msg.sessionId, k -> new CopyOnWriteArrayList<>()).add(msg);
        return Uni.createFrom().item(toChatMessageProto(msg));
    }

    @Override
    public Multi<ChatMessage> observeSession(ObserveSessionRequest request) {
        return Multi.createFrom().empty();
    }

    @Override
    public Multi<RunAgentEvent> runAgent(RunAgentRequest request) {
        final String targetSession = request.getSessionId() != null && !request.getSessionId().isBlank() 
            ? request.getSessionId() 
            : "general-session";
        log.info("Received runAgent request for session: " + targetSession);

        AgentRequest agentReq = AgentRequest.builder()
                .sessionId(targetSession)
                .content(request.getPrompt() != null ? request.getPrompt() : "")
                .build();

        // Stream of plain text responses from orchestrator
        Multi<RunAgentEvent> textStream = orchestrator != null 
                ? orchestrator.stream(agentReq)
                    .map(response -> {
                        String text = response != null && response.content() != null ? response.content() : "";
                        return RunAgentEvent.newBuilder()
                                .setType("TEXT")
                                .setContent(text)
                                .build();
                    })
                : Multi.createFrom().item(RunAgentEvent.newBuilder()
                    .setType("TEXT")
                    .setContent("Aljabr Agent Ready: Workspace verified.")
                    .build());

        // Stream of recorded AgentEvent from the EventLedger
        Multi<RunAgentEvent> ledgerStream = Multi.createFrom().emitter(emitter -> {
            java.util.function.Consumer<AgentEvent> listener = ev -> {
                try {
                    java.util.Map data = mapper.convertValue(ev, java.util.Map.class);
                    data.put("eventType", ev.getClass().getSimpleName());
                    String payload = mapper.writeValueAsString(data);
                    RunAgentEvent evMsg = RunAgentEvent.newBuilder()
                            .setType(ev.getClass().getSimpleName())
                            .setPayload(payload)
                            .build();
                    emitter.emit(evMsg);
                } catch (Exception ex) {
                    // ignore
                }
            };
            if (eventLedger != null) {
                eventLedger.registerListener(listener);
                emitter.onTermination(() -> eventLedger.unregisterListener(listener));
            } else {
                emitter.complete();
            }
        });

        // Merge both streams; ensure a DONE event when orchestrator completes
        return Multi.createBy().merging().streams(textStream, ledgerStream)
                .onCompletion().continueWith(RunAgentEvent.newBuilder().setType("DONE").setContent("Completed").build());
    }

    private ChatMessage toChatMessageProto(ChatMessageEntity m) {
        return ChatMessage.newBuilder()
                .setId(m.id != null ? m.id : "")
                .setSessionId(m.sessionId != null ? m.sessionId : "")
                .setRole(m.role != null ? m.role : "")
                .setContent(m.content != null ? m.content : "")
                .setToolCalls(m.toolCalls != null ? m.toolCalls : "")
                .setTokens(m.tokens != null ? m.tokens : 0)
                .setModel(m.model != null ? m.model : "")
                .setCreatedAt(m.createdAt != null ? m.createdAt : "")
                .build();
    }
}
