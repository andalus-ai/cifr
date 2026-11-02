package tech.kayys.cifr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkspaceOverlay implements WorkspaceOverlay {

    private final Path workspaceRoot;
    private final Map<Path, String> staged = new ConcurrentHashMap<>();

    public InMemoryWorkspaceOverlay(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    @Override
    public Optional<String> read(Path file) {
        Path resolved = workspaceRoot != null && !file.isAbsolute() ? workspaceRoot.resolve(file) : file;
        if (staged.containsKey(resolved)) {
            return Optional.of(staged.get(resolved));
        }
        try {
            if (Files.exists(resolved)) {
                return Optional.of(Files.readString(resolved));
            }
        } catch (Exception e) {
            // ignore
        }
        return Optional.empty();
    }

    @Override
    public Uni<Void> stage(Path file, String content) {
        return Uni.createFrom().item(() -> {
            Path resolved = workspaceRoot != null && !file.isAbsolute() ? workspaceRoot.resolve(file) : file;
            staged.put(resolved, content);
            return null;
        });
    }

    @Override
    public Uni<Void> commit() {
        return Uni.createFrom().item(() -> {
            for (Map.Entry<Path, String> entry : staged.entrySet()) {
                try {
                    Path parent = entry.getKey().getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to commit staged file: " + entry.getKey(), e);
                }
            }
            staged.clear();
            return null;
        });
    }

    @Override
    public Uni<Void> rollback() {
        return Uni.createFrom().item(() -> {
            staged.clear();
            return null;
        });
    }

    @Override
    public Map<Path, String> stagedChanges() {
        return Map.copyOf(staged);
    }
}
