package tech.kayys.cifr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.andalus.pro.api.grpc.*;
import tech.kayys.andalus.agent.AgentRequest;
import tech.kayys.cifr.orchestration.CoderOrchestrator;
import tech.kayys.cifr.api.entity.ChatMessageEntity;
import tech.kayys.cifr.api.repository.ChatMessageRepository;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.logging.Logger;

@GrpcService
@jakarta.inject.Singleton
public class ChatServiceImpl extends MutinyChatServiceGrpc.ChatServiceImplBase implements ChatService {

    private static final Logger log = Logger.getLogger(ChatServiceImpl.class.getName());

    @Inject
    CoderOrchestrator orchestrator;

    @Inject
    ChatMessageRepository messageRepo;

    // In-memory fallback message storage per session
    private final Map<String, List<ChatMessageEntity>> memoryMessages = new ConcurrentHashMap<>();

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private final java.io.File messagesFile;

    public ChatServiceImpl() {
        String home = System.getProperty("user.home");
        java.io.File dbDir = new java.io.File(home, ".andalus/backend_data");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        messagesFile = new java.io.File(dbDir, "messages.json");
        loadState();
    }

    private synchronized void loadState() {
        try {
            if (messagesFile.exists()) {
                Map<String, List<ChatMessageEntity>> map = MAPPER.readValue(messagesFile, new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<ChatMessageEntity>>>() {});
                for (Map.Entry<String, List<ChatMessageEntity>> entry : map.entrySet()) {
                    memoryMessages.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
                }
            }
        } catch (Exception e) {
            log.warning("Failed to load chat state: " + e.getMessage());
        }
    }

    private synchronized void saveState() {
        try {
            MAPPER.writeValue(messagesFile, memoryMessages);
        } catch (Exception e) {
            log.warning("Failed to save chat state: " + e.getMessage());
        }
    }

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
        saveState();
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

        AgentRequest.AgentRequestBuilder agentReqBuilder = AgentRequest.builder()
                .sessionId(targetSession)
                .content(request.getPrompt() != null ? request.getPrompt() : "");

        if (request.getModelId() != null && !request.getModelId().isBlank()) {
            agentReqBuilder.parameter("model", request.getModelId());
        }
        if (request.getProviderId() != null && !request.getProviderId().isBlank()) {
            agentReqBuilder.parameter("provider", request.getProviderId());
        }
        // Pass the workspace path from the selected project so agent tools operate in the correct directory
        if (request.getWorkspacePath() != null && !request.getWorkspacePath().isBlank()) {
            agentReqBuilder.parameter("workspace_path", request.getWorkspacePath());
            log.info("Workspace path set from request: " + request.getWorkspacePath());
        }

        AgentRequest agentReq = agentReqBuilder.build();

        // Emit streaming tokens from orchestrator, then a DONE sentinel
        Multi<RunAgentEvent> textStream = orchestrator != null
                ? orchestrator.stream(agentReq)
                    .map(response -> {
                        String text = response != null && response.content() != null ? response.content() : "";
                        String type = response != null && response.type() != null ? response.type() : "TEXT";
                        return RunAgentEvent.newBuilder()
                                .setType(type)
                                .setContent(text)
                                .build();
                    })
                    .onFailure().recoverWithItem(err ->
                            RunAgentEvent.newBuilder()
                                    .setType("ERROR")
                                    .setContent("Agent error: " + err.getMessage())
                                    .build())
                : Multi.createFrom().item(RunAgentEvent.newBuilder()
                    .setType("TEXT")
                    .setContent("Cifr Agent Ready: Workspace verified.")
                    .build());

        // Drive the stream from textStream only; append DONE when it finishes
        return textStream
                .onCompletion().continueWith(
                        RunAgentEvent.newBuilder().setType("DONE").setContent("Completed").build());
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
