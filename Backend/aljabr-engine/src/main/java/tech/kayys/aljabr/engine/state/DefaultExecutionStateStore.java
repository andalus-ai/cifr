package tech.kayys.aljabr.engine.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.extension.Id;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultExecutionStateStore implements ExecutionStateStore {
    private static final Logger log = Logger.getLogger(DefaultExecutionStateStore.class.getName());
    private static final Path STORE_DIR = Paths.get(System.getProperty("user.dir"), ".aljabr", "execution");

    private final ObjectMapper mapper;
    private final Map<String, ExecutionState> activeStates = new HashMap<>();

    public DefaultExecutionStateStore() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(STORE_DIR)) {
                Files.createDirectories(STORE_DIR);
            }
        } catch (IOException e) {
            log.severe("Failed to initialize execution store: " + e.getMessage());
        }
    }

    @Override
    public Optional<ExecutionState> get(String executionId) {
        if (activeStates.containsKey(executionId)) {
            return Optional.of(activeStates.get(executionId));
        }
        
        Path filePath = STORE_DIR.resolve(executionId + ".json");
        if (Files.exists(filePath)) {
            try {
                ExecutionState state = mapper.readValue(filePath.toFile(), ExecutionState.class);
                activeStates.put(executionId, state);
                return Optional.of(state);
            } catch (IOException e) {
                log.warning("Failed to load state for " + executionId + ": " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Override
    public ExecutionState transition(String executionId, AgentPhase phase, Map<String, Object> metadata) {
        ExecutionState current = get(executionId).orElseGet(() -> 
            new ExecutionState(executionId, AgentPhase.OBSERVING, 1, null, null, 0, 0, Instant.now(), Instant.now(), new HashMap<>())
        );

        ExecutionState next = new ExecutionState(
                current.executionId(),
                phase,
                current.attempt(),
                Id.random().asString(), // new checkpointId
                current.lastEventId(),
                current.inputTokens(),
                current.outputTokens(),
                current.startedAt(),
                Instant.now(),
                metadata != null ? new HashMap<>(metadata) : current.metadata()
        );

        activeStates.put(executionId, next);
        checkpoint(next);
        return next;
    }

    @Override
    public void checkpoint(ExecutionState state) {
        try {
            Path filePath = STORE_DIR.resolve(state.executionId() + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), state);
            log.info("Checkpoint saved for execution " + state.executionId() + " at phase " + state.phase());
        } catch (IOException e) {
            log.warning("Failed to checkpoint execution state: " + e.getMessage());
        }
    }

    @Override
    public void complete(String executionId, AgentResponse response) {
        log.info("Execution " + executionId + " completed");
    }

    @Override
    public void fail(String executionId, Throwable error) {
        log.severe("Execution " + executionId + " failed: " + error.getMessage());
    }
}
