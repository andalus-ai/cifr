package tech.kayys.cifr.engine.index.reference;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.ide.CifrIdeWorkspaceRegistry;
import tech.kayys.cifr.engine.index.symbol.SourceRange;
import tech.kayys.cifr.engine.index.symbol.SymbolId;
import tech.kayys.cifr.engine.index.symbol.SymbolIndex;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Resolves references through the LSP instance owned by CIFR IDE. */
@ApplicationScoped
public class LspReferenceResolver implements ReferenceResolver {
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);
    private static final int DEFAULT_LIMIT = 500;

    @Inject CifrIdeWorkspaceRegistry workspaces;
    @Inject SymbolIndex symbols;

    @Override
    public boolean supports(FileRecord record) {
        return record != null && record.path() != null
                && record.workspaceId() != null && !record.workspaceId().isBlank();
    }

    @Override
    public Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target) {
        return resolveReferences(record, target, DEFAULT_LIMIT);
    }

    @Override
    public Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target, int limit) {
        if (!supports(record) || target == null || limit <= 0) {
            return Uni.createFrom().item(List.of());
        }
        return symbols.getSymbol(target, record.workspaceId()).onItem().transformToUni(symbol -> {
            if (symbol == null || symbol.file() == null || !samePath(symbol.file(), record.path())) {
                return Uni.createFrom().item(List.of());
            }
            return Uni.createFrom().item(() -> resolveFromIde(record, symbol, target, limit));
        });
    }

    private List<Reference> resolveFromIde(FileRecord record, SymbolInfo symbol, SymbolId target, int limit) {
        try {
            CifrIdeWorkspaceRegistry.Session session = workspaces.require(record.workspaceId());
            Path root = session.root().toRealPath();
            Path source = root.resolve(record.path()).normalize();
            if (!source.startsWith(root)) {
                throw new IOException("Indexed source path escapes workspace root: " + record.path());
            }
            source = source.toRealPath();
            if (!source.startsWith(root)) {
                throw new IOException("Indexed source resolves outside workspace root: " + record.path());
            }

            SourceRange definition = symbol.range();
            if (definition == null) {
                throw new IOException("Symbol has no definition range: " + symbol.name());
            }

            JsonNode document = session.client().invoke(record.workspaceId(), "doc_open",
                    Map.of("path", source.toString()));
            int documentId = document.path("id").asInt(-1);
            if (documentId < 0) {
                throw new IOException("CIFR IDE doc_open returned no document id for " + source);
            }

            try {
                int line = Math.max(0, definition.startLine() - 1);
                int column = Math.max(0, definition.startColumn() - 1);
                JsonNode response = session.client().invoke(record.workspaceId(), "lsp_references",
                        Map.of(
                                "request", nextRequestId(),
                                "doc", documentId,
                                "pos", Map.of("line", line, "col", column),
                                "includeDeclaration", true,
                                "projectWide", true));
                JsonNode locations = response.path("locations");
                if (!locations.isArray()) {
                    throw new IOException("CIFR IDE lsp_references returned an invalid response");
                }

                List<Reference> references = new ArrayList<>();
                for (JsonNode location : locations) {
                    if (references.size() >= limit) break;
                    String rawPath = location.path("path").asText("");
                    if (rawPath.isBlank() || isNonFileUri(rawPath)) continue;
                    Path path = Path.of(rawPath);
                    if (!path.isAbsolute()) path = root.resolve(path);
                    JsonNode range = location.path("range");
                    JsonNode start = range.path("start");
                    JsonNode end = range.path("end");
                    if (!start.has("line") || !start.has("col") || !end.has("line") || !end.has("col")) {
                        continue;
                    }
                    SourceRange sourceRange = new SourceRange(
                            start.path("line").asInt() + 1,
                            end.path("line").asInt() + 1,
                            start.path("col").asInt() + 1,
                            end.path("col").asInt() + 1);
                    references.add(new Reference(target, path.normalize(), sourceRange, ReferenceKind.UNKNOWN));
                }
                return references;
            } finally {
                // The headless host is owned by this workspace session. Release the
                // temporary document so repeated repository queries do not leak buffers.
                try {
                    session.client().invoke(record.workspaceId(), "doc_close", Map.of("doc", documentId));
                } catch (Exception ignored) {
                    // Process shutdown is the final cleanup boundary.
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIFR IDE reference lookup interrupted for workspace "
                    + record.workspaceId() + " and file " + record.path(), e);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("CIFR IDE reference lookup failed for workspace "
                    + record.workspaceId() + " and file " + record.path(), e);
        }
    }

    private static int nextRequestId() {
        return REQUEST_IDS.updateAndGet(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
    }

    private static boolean samePath(Path left, Path right) {
        return left != null && right != null && left.normalize().equals(right.normalize());
    }

    private static boolean isNonFileUri(String value) {
        int colon = value.indexOf(':');
        return colon > 1 && !value.regionMatches(true, 0, "file:", 0, 5);
    }
}
