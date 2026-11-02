package tech.kayys.cifr.engine.index.ide;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.ide.CifrIdeClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Owns one CIFR IDE server session per workspace. The workspace ID is also
 * the IDE window key, preventing relative paths from leaking across projects.
 */
@ApplicationScoped
public class CifrIdeWorkspaceRegistry {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public synchronized void openWorkspace(String workspaceId, Path root)
            throws IOException, InterruptedException, TimeoutException {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (root == null || !root.toAbsolutePath().normalize().toFile().isDirectory()) {
            throw new IllegalArgumentException("workspace root must be an existing directory");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Session existing = sessions.get(workspaceId);
        if (existing != null && existing.root().equals(normalizedRoot)) return;
        closeWorkspace(workspaceId);
        List<String> command = configuredCommand();
        CifrIdeClient client = CifrIdeClient.start(command, root, Duration.ofSeconds(30), null);
        try {
            JsonNode project = client.invoke(workspaceId, "open_project",
                    Map.of("path", root.toAbsolutePath().normalize().toString()));
            if (project == null || project.isNull()) {
                throw new IOException("CIFR IDE did not return project information for " + workspaceId);
            }
            sessions.put(workspaceId, new Session(normalizedRoot, client));
        } catch (IOException | InterruptedException | TimeoutException | RuntimeException e) {
            client.close();
            throw e;
        }
    }

    public boolean isOpen(String workspaceId) {
        return workspaceId != null && sessions.containsKey(workspaceId);
    }

    public Session require(String workspaceId) {
        Session session = sessions.get(workspaceId);
        if (session == null) {
            throw new IllegalStateException("No CIFR IDE session is open for workspace " + workspaceId);
        }
        return session;
    }

    public synchronized void closeWorkspace(String workspaceId) {
        Session session = sessions.remove(workspaceId);
        if (session == null) return;
        try {
            session.client().invoke(workspaceId, "close_project", Map.of());
        } catch (Exception ignored) {
            // Closing the process below is authoritative; project close is best-effort.
        } finally {
            session.client().close();
        }
    }

    private List<String> configuredCommand() {
        String configured = System.getenv().getOrDefault("CIFR_IDE_SERVER_COMMAND", "cifridea-server --stdio");
        // Deliberately no shell expansion: operators configure an executable plus arguments.
        List<String> parts = List.of(configured.trim().split("\\s+"));
        if (parts.isEmpty() || parts.get(0).isBlank()) {
            throw new IllegalStateException("CIFR_IDE_SERVER_COMMAND is empty");
        }
        return parts;
    }

    @PreDestroy
    void shutdown() {
        sessions.keySet().forEach(this::closeWorkspace);
    }

    public record Session(Path root, CifrIdeClient client) { }
}
