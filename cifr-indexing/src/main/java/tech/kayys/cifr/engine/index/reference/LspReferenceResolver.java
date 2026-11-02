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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses the IDE language server's semantic reference search at the real definition position. */
@ApplicationScoped
public class LspReferenceResolver implements ReferenceResolver {
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);

    @Inject CifrIdeWorkspaceRegistry workspaces;
    @Inject SymbolIndex symbolIndex;

    @Override
    public boolean supports(FileRecord record) {
        return record != null && record.path() != null
                && record.workspaceId() != null && !record.workspaceId().isBlank()
                && workspaces != null && workspaces.isOpen(record.workspaceId());
    }

    @Override
    public Uni<List<Reference>> resolveReferences(FileRecord record, SymbolId target) {
        if (!supports(record) || target == null || symbolIndex == null) {
            return Uni.createFrom().item(List.of());
        }
        return symbolIndex.getSymbol(target, record.workspaceId()).onItem().transformToUni(symbol -> {
            if (symbol == null || symbol.range() == null || symbol.range().startColumn() <= 0) {\n                return Uni.createFrom().item(List.of());\n            }
            return Uni.createFrom().item(() -> queryIde(record, symbol));
        });
    }

    private List<Reference> queryIde(FileRecord record, SymbolInfo symbol) {
        try {
            var session = workspaces.require(record.workspaceId());
            Path absolutePath = session.root().resolve(symbol.file()).normalize();
            JsonNode document = session.client().invoke(record.workspaceId(), "doc_open",
                    java.util.Map.of("path", absolutePath.toString()));
            int docId = document.path("id").asInt(-1);
            if (docId < 0) throw new IOException("CIFR IDE doc_open returned no document id");

            SourceRange range = symbol.range();
            JsonNode result = session.client().invoke(record.workspaceId(), "lsp_references",
                    java.util.Map.of(
                            "request", REQUEST_IDS.getAndIncrement(),
                            "doc", docId,
                            "pos", java.util.Map.of(
                                    "line", Math.max(0, range.startLine() - 1),
                                    "character", Math.max(0, range.startColumn() - 1)),
                            "includeDeclaration", true,
                            "projectWide", true));
            JsonNode locations = result.path("locations");
            List<Reference> references = new ArrayList<>();
            if (!locations.isArray()) return references;
            for (JsonNode location : locations) {
                String path = location.path("path").asText("");
                JsonNode source = location.path("range");
                JsonNode start = source.path("start");
                JsonNode end = source.path("end");
                if (path.isBlank() || !start.has("line") || !start.has("character")
                        || !end.has("line") || !end.has("character")) continue;
                SourceRange sourceRange = new SourceRange(
                        start.path("line").asInt() + 1,
                        end.path("line").asInt() + 1,
                        start.path("character").asInt() + 1,
                        end.path("character").asInt() + 1);
                references.add(new Reference(target, Path.of(path), sourceRange, ReferenceKind.UNKNOWN));
            }
            return references;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIFR IDE reference lookup interrupted for " + record.path(), e);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("CIFR IDE reference lookup failed for " + record.path(), e);
        }
    }
}
