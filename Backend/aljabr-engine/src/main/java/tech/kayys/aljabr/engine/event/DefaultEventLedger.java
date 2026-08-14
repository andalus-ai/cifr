package tech.kayys.aljabr.engine.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.agent.AgentEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultEventLedger implements EventLedger {
    private static final Logger log = Logger.getLogger(DefaultEventLedger.class.getName());
    private static final Path LEDGER_DIR = Paths.get(System.getProperty("user.dir"), ".aljabr", "ledger");
    private static final Path EVENTS_FILE = LEDGER_DIR.resolve("events.json");

    private final ObjectMapper mapper;
    private final List<AgentEvent> events = new ArrayList<>();

    public DefaultEventLedger() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(LEDGER_DIR)) {
                Files.createDirectories(LEDGER_DIR);
            }
            loadEvents();
        } catch (IOException e) {
            log.severe("Failed to initialize Aljabr event ledger: " + e.getMessage());
        }
    }

    @Override
    public void record(AgentEvent event) {
        log.info("EventLedger recording: " + event.getClass().getSimpleName());
        events.add(event);
        saveEvents();
    }

    private void loadEvents() throws IOException {
        File file = EVENTS_FILE.toFile();
        if (file.exists()) {
            List<AgentEvent> loaded = mapper.readValue(file, new TypeReference<List<AgentEvent>>() {});
            events.addAll(loaded);
        }
    }

    private void saveEvents() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(EVENTS_FILE.toFile(), events);
        } catch (IOException e) {
            log.warning("Failed to save event ledger: " + e.getMessage());
        }
    }
}
