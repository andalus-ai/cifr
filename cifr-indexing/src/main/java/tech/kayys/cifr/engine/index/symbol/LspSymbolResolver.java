package tech.kayys.cifr.engine.index.symbol;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.ide.CifrIdeWorkspaceRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/** Resolves symbols from the workspace index owned by CIFR IDE. */
@ApplicationScoped
public class LspSymbolResolver implements SymbolResolver {
    private static final int MAX_SYMBOLS = 2_000;

    @Inject CifrIdeWorkspaceRegistry workspaces;

    @Override
    public boolean supports(FileRecord record) {
        return record != null && record.path() != null
                && record.workspaceId() != null && !record.workspaceId().isBlank()
                && workspaces != null && workspaces.isOpen(record.workspaceId());
    }

    @Override
    public Uni<List<SymbolInfo>> resolve(FileRecord record) {
        return Uni.createFrom().item(() -> resolveFromIde(record));
    }

    private List<SymbolInfo> resolveFromIde(FileRecord record) {
        try {
            var session = workspaces.require(record.workspaceId());
            JsonNode response = session.client().invoke(record.workspaceId(), "find_symbols",
                    java.util.Map.of("query", "", "filter", "all", "limit", MAX_SYMBOLS));
            List<SymbolInfo> symbols = new ArrayList<>();
            if (response == null || !response.isArray()) return symbols;
            for (JsonNode entry : response) {
                String relativePath = entry.path("relPath").asText("");
                if (!samePath(relativePath, record.path().toString())) continue;
                String name = entry.path("name").asText("");
                if (name.isBlank()) continue;
                String container = entry.path("container").isNull() ? null : entry.path("container").asText(null);
                String qualifiedName = container == null || container.isBlank() ? name : container + "." + name;
                JsonNode range = entry.path("range");
                JsonNode start = range.path("start");
                JsonNode end = range.path("end");
                if (!start.has("line") || !start.has("character") || !end.has("line") || !end.has("character")) {
                    continue;
                }
                SourceRange sourceRange = new SourceRange(
                        start.path("line").asInt() + 1,
                        end.path("line").asInt() + 1,
                        start.path("character").asInt() + 1,
                        end.path("character").asInt() + 1);
                SymbolId id = new SymbolId(qualifiedName, container == null ? "" : container, record.contentHash());
                symbols.add(new SymbolInfo(id, name, mapKind(entry.path("kind").asText("")),
                        record.path(), sourceRange, Optional.empty(), record.workspaceId()));
            }
            return symbols;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIFR IDE symbol lookup interrupted for workspace "
                    + record.workspaceId() + " and file " + record.path(), e);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("CIFR IDE symbol lookup failed for workspace "
                    + record.workspaceId() + " and file " + record.path(), e);
        }
    }

    private static boolean samePath(String left, String right) {
        return left.replace('\\', '/').equals(right.replace('\\', '/'));
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
