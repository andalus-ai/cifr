package tech.kayys.cifr.engine.index.symbol;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.ide.CifrIdeWorkspaceRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Resolves symbols from the workspace index owned by CIFR IDE. */
@ApplicationScoped
public class LspSymbolResolver implements SymbolResolver {
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private final Map<String, List<SymbolInfo>> workspaceSnapshots = new ConcurrentHashMap<>();

    @Inject CifrIdeWorkspaceRegistry workspaces;

    @Override
    public boolean supports(FileRecord record) {
        return record != null && record.path() != null
                && record.workspaceId() != null && !record.workspaceId().isBlank()
                && workspaces != null && workspaces.isOpen(record.workspaceId());
    }

    @Override
    public Uni<List<SymbolInfo>> resolve(FileRecord record) {
        return Uni.createFrom().item(() -> {
            List<SymbolInfo> all = workspaceSnapshots.get(record.workspaceId());
            if (all == null) {
                synchronized (workspaceSnapshots) {
                    all = workspaceSnapshots.get(record.workspaceId());
                    if (all == null) {
                        all = loadWorkspaceSymbols(record);
                        if (!all.isEmpty()) workspaceSnapshots.put(record.workspaceId(), List.copyOf(all));
                    }
                }
            }
            return all.stream().filter(symbol -> samePath(symbol.file(), record.path())).toList();
        });
    }

    @Override
    public void invalidateWorkspace(String workspaceId) {
        if (workspaceId != null) workspaceSnapshots.remove(workspaceId);
    }

    private List<SymbolInfo> loadWorkspaceSymbols(FileRecord record) {
        try {
            var session = workspaces.require(record.workspaceId());
            JsonNode response = session.client().invoke(record.workspaceId(), "lsp_workspace_symbols",
                    java.util.Map.of("request", REQUEST_IDS.getAndIncrement(), "query", ""));
            List<SymbolInfo> symbols = new ArrayList<>();
            if (response == null || !response.isArray()) return symbols;
            for (JsonNode entry : response) {
                JsonNode location = entry.path("location");
                String relativePath = location.path("path").asText("");
                String name = entry.path("name").asText("");
                if (relativePath.isBlank() || name.isBlank()) continue;
                String container = entry.path("container").isNull() ? null : entry.path("container").asText(null);
                String qualifiedName = container == null || container.isBlank() ? name : container + "." + name;
                JsonNode range = location.path("range");
                JsonNode start = range.path("start");
                JsonNode end = range.path("end");
                if (!start.has("line") || !start.has("character") || !end.has("line") || !end.has("character")) continue;
                SourceRange sourceRange = new SourceRange(
                        start.path("line").asInt() + 1,
                        end.path("line").asInt() + 1,
                        start.path("character").asInt() + 1,
                        end.path("character").asInt() + 1);
                Path sourceFile = Path.of(relativePath);
                String contentHash = samePath(sourceFile, record.path()) ? record.contentHash() : "";
                SymbolId id = new SymbolId(qualifiedName, container == null ? "" : container, contentHash);
                symbols.add(new SymbolInfo(id, name, mapKind(entry.path("kind").asText("")),
                        sourceFile, sourceRange, Optional.empty(), record.workspaceId()));
            }
            return symbols;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIFR IDE symbol lookup interrupted for workspace "
                    + record.workspaceId(), e);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("CIFR IDE symbol lookup failed for workspace "
                    + record.workspaceId(), e);
        }
    }

    private static boolean samePath(Path left, Path right) {
        if (left == null || right == null) return false;
        return left.normalize().toString().replace('\\', '/')
                .equals(right.normalize().toString().replace('\\', '/'));
    }

    private static SymbolKind mapKind(String value) {
        return switch (value.toLowerCase()) {
            case "class", "struct", "trait", "type" -> SymbolKind.CLASS;
            case "interface" -> SymbolKind.INTERFACE;
            case "enum" -> SymbolKind.ENUM;
            case "method" -> SymbolKind.METHOD;
            case "function" -> SymbolKind.FUNCTION;
            case "field" -> SymbolKind.FIELD;
            case "property" -> SymbolKind.PROPERTY;
            case "constructor" -> SymbolKind.CONSTRUCTOR;
            case "module", "namespace", "package" -> SymbolKind.MODULE;
            default -> SymbolKind.VARIABLE;
        };
    }
}
