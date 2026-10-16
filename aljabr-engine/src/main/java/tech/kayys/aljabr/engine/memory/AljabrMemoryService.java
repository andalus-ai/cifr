package tech.kayys.aljabr.engine.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.andalus.extension.Id;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import java.util.Optional;
import tech.kayys.aljabr.engine.cache.ExecutionCache;

@ApplicationScoped
public class AljabrMemoryService implements MemoryLifecycle {
    private static final Logger log = Logger.getLogger(AljabrMemoryService.class.getName());
    private static final Path MEMORY_DIR = Paths.get(System.getProperty("user.dir"), ".aljabr", "memory");
    private static final Path FACTS_FILE = MEMORY_DIR.resolve("facts.json");
    private static final Path RESEARCH_FILE = MEMORY_DIR.resolve("research.json");

    private final ObjectMapper mapper;
    private final List<MemoryFact> facts = new ArrayList<>();
    private final List<ResearchArtifact> artifacts = new ArrayList<>();

    public AljabrMemoryService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(MEMORY_DIR)) {
                Files.createDirectories(MEMORY_DIR);
            }
            loadFacts();
            loadResearch();
            
            // For testing: Inject a default project memory if none exist
            if (facts.isEmpty()) {
                addFact(MemoryLayer.PROJECT, "This is a Quarkus/Maven project.");
            }
        } catch (IOException e) {
            log.severe("Failed to initialize Aljabr memory storage: " + e.getMessage());
        }
    }

    public void addFact(MemoryLayer layer, String content) {
        MemoryFact fact = new MemoryFact(Id.random().asString(), content, layer, Instant.now());
        facts.add(fact);
        saveFacts();
    }

    public List<MemoryFact> getFactsForLayer(MemoryLayer layer) {
        return facts.stream()
                .filter(f -> f.layer() == layer)
                .collect(Collectors.toList());
    }

    public void addResearch(String query, String source, String contentHash, String summary, 
                            String tool, String executionId, String stepId, List<String> parentArtifacts, double confidence) {
        ResearchArtifact artifact = new ResearchArtifact(Id.random().asString(), query, source, contentHash, summary, Instant.now(),
                                                         tool, executionId, stepId, parentArtifacts, confidence);
        artifacts.add(artifact);
        saveResearch();
    }

    public List<ResearchArtifact> getAllResearch() {
        return List.copyOf(artifacts);
    }

    private void loadFacts() throws IOException {
        File file = FACTS_FILE.toFile();
        if (file.exists()) {
            List<MemoryFact> loaded = mapper.readValue(file, new TypeReference<List<MemoryFact>>() {});
            facts.addAll(loaded);
        }
    }

    private void saveFacts() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(FACTS_FILE.toFile(), facts);
        } catch (IOException e) {
            log.warning("Failed to save memory facts: " + e.getMessage());
        }
    }

    private void loadResearch() throws IOException {
        File file = RESEARCH_FILE.toFile();
        if (file.exists()) {
            List<ResearchArtifact> loaded = mapper.readValue(file, new TypeReference<List<ResearchArtifact>>() {});
            artifacts.addAll(loaded);
        }
    }

    private void saveResearch() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(RESEARCH_FILE.toFile(), artifacts);
        } catch (IOException e) {
            log.warning("Failed to save research artifacts: " + e.getMessage());
        }
    }

    @Override
    public void persist(MemoryCandidate candidate, MemoryPolicy policy) {
        MemoryLayer layer = switch (policy.scope()) {
            case USER -> MemoryLayer.USER;
            case PROJECT -> MemoryLayer.PROJECT;
            default -> MemoryLayer.SESSION;
        };
        addFact(layer, candidate.content());
    }

    @Override
    public void archive(String id) {
        // MVP: Just delete for now
        delete(id);
    }

    @Override
    public void delete(String id) {
        Optional<MemoryFact> factOpt = getFactById(id);
        if (factOpt.isPresent()) {
            facts.remove(factOpt.get());
            saveFacts();
            log.info("Deleted fact " + id);
            
            // Cascading delete for derived research and caches
            List<ResearchArtifact> toRemove = new ArrayList<>();
            for (ResearchArtifact artifact : artifacts) {
                if (artifact.parentArtifacts() != null && artifact.parentArtifacts().contains(id)) {
                    toRemove.add(artifact);
                }
            }
            if (!toRemove.isEmpty()) {
                artifacts.removeAll(toRemove);
                saveResearch();
                log.info("Cascaded delete to " + toRemove.size() + " derived research artifacts");
            }
        }
    }

    @Override
    public Optional<MemoryFact> getFactById(String id) {
        return facts.stream().filter(f -> f.id().equals(id)).findFirst();
    }
}
