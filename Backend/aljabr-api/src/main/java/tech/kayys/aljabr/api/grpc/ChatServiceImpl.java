package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
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

    @Override
    public Uni<ListChatMessagesResponse> listMessages(ListChatMessagesRequest request) {
        return messageRepo.find("sessionId", request.getSessionId()).list().map(entities -> {
            List<ChatMessage> messages = entities.stream()
                .map(this::toChatMessageProto)
                .collect(Collectors.toList());
            return ListChatMessagesResponse.newBuilder()
                    .addAllMessages(messages)
                    .build();
        });
    }

    @Override
    @WithTransaction
    public Uni<ChatMessage> addMessage(AddChatMessageRequest request) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.id = UUID.randomUUID().toString();
        msg.sessionId = request.getSessionId();
        msg.role = request.getRole();
        msg.content = request.getContent();
        msg.toolCalls = request.getToolCalls();
        msg.tokens = request.getTokens();
        msg.model = request.getModel();
        msg.createdAt = Instant.now().toString();

        return messageRepo.persist(msg).map(this::toChatMessageProto);
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

        // Stream of plain text responses from orchestrator
        Multi<RunAgentEvent> textStream = orchestrator.stream(agentReq)
                .map(response -> {
                    String text = response.content() != null ? response.content() : "";
                    return RunAgentEvent.newBuilder()
                            .setType("TEXT")
                            .setContent(text)
                            .build();
                });

        // Stream of recorded AgentEvent from the EventLedger
        Multi<RunAgentEvent> ledgerStream = Multi.createFrom().emitter(emitter -> {
            java.util.function.Consumer<AgentEvent> listener = ev -> {
                try {
                    // convert event to map and serialize as payload JSON
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
            eventLedger.registerListener(listener);
            emitter.onTermination(() -> eventLedger.unregisterListener(listener));
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
