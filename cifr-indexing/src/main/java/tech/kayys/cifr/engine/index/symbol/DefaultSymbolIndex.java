package tech.kayys.cifr.engine.index.symbol;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileIndex;
import tech.kayys.cifr.engine.index.FileRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultSymbolIndex implements SymbolIndex {
    private final Map<String, SymbolInfo> symbolsById = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SymbolInfo>> symbolsByFile = new ConcurrentHashMap<>();

    @Inject SymbolResolver symbolResolver;
    @Inject FileIndex fileIndex;

    @Override
    public Uni<List<SymbolInfo>> getSymbols(Path file) {
        return getSymbols(file, "default-workspace");
    }

    @Override
    public Uni<List<SymbolInfo>> getSymbols(Path file, String workspaceId) {
        if (file == null || workspaceId == null || workspaceId.isBlank()) return Uni.createFrom().item(List.of());
        List<SymbolInfo> cached = symbolsByFile.get(fileKey(workspaceId, file));
        if (cached != null && !cached.isEmpty()) return Uni.createFrom().item(new ArrayList<>(cached));
        if (fileIndex == null || symbolResolver == null) return Uni.createFrom().item(List.of());
        return fileIndex.getCurrentIndex(workspaceId).onItem().transformToUni(records -> {
            FileRecord record = records.stream().filter(r -> samePath(r.path(), file)).findFirst().orElse(null);
            if (record == null || !symbolResolver.supports(record)) return Uni.createFrom().item(List.of());
            return symbolResolver.resolve(record).onItem().invoke(found -> found.forEach(this::registerSymbol));
        });
    }

    @Override
    public Uni<List<SymbolInfo>> searchSymbols(String query) {
        return searchSymbols(query, null);
    }

    @Override
    public Uni<List<SymbolInfo>> searchSymbols(String query, String workspaceId) {
        String lower = query == null ? "" : query.toLowerCase();
        return Uni.createFrom().item(() -> symbolsById.values().stream()
                .filter(s -> workspaceId == null || workspaceId.equals(s.workspaceId()))
                .filter(s -> lower.isBlank() || s.name().toLowerCase().contains(lower)
                        || (s.id() != null && s.id().qualifiedName().toLowerCase().contains(lower)))
                .collect(Collectors.toList()));
    }

    @Override
    public Uni<SymbolInfo> getSymbol(SymbolId id) {
        return findSymbol(id, null);
    }

    @Override
    public Uni<SymbolInfo> getSymbol(SymbolId id, String workspaceId) {
        return findSymbol(id, workspaceId == null || workspaceId.isBlank() ? null : workspaceId);
    }

    private Uni<SymbolInfo> findSymbol(SymbolId id, String workspaceId) {
        if (id == null) return Uni.createFrom().nullItem();
        List<SymbolInfo> matches = symbolsById.values().stream()
                .filter(s -> workspaceId == null || workspaceId.equals(s.workspaceId()))
                .filter(s -> s.id() != null && (s.id().equals(id)
                        || s.name().equalsIgnoreCase(id.qualifiedName())
                        || s.id().qualifiedName().equalsIgnoreCase(id.qualifiedName())))
                .toList();
        // Never choose an arbitrary project's symbol for an unscoped ambiguous name.
        return Uni.createFrom().item(matches.size() == 1 ? matches.get(0) : null);
    }

    @Override
    public void registerSymbol(SymbolInfo info) {
        if (info == null) return;
        String qualified = info.id() == null ? info.name() : info.id().qualifiedName();
        String key = info.workspaceId() + "|" + (info.file() == null ? "" : info.file()) + "|" + qualified;
        SymbolInfo previous = symbolsById.put(key, info);
        if (info.file() != null) {
            CopyOnWriteArrayList<SymbolInfo> values =
                    symbolsByFile.computeIfAbsent(fileKey(info.workspaceId(), info.file()), ignored -> new CopyOnWriteArrayList<>());
            if (previous != null) values.remove(previous);
            values.addIfAbsent(info);
        }
    }

    @Override
    public void clear() {
        symbolsById.clear();
        symbolsByFile.clear();
    }

    @Override
    public void clearWorkspace(String workspaceId) {
        if (workspaceId == null) return;
        symbolsById.entrySet().removeIf(entry -> workspaceId.equals(entry.getValue().workspaceId()));
        symbolsByFile.entrySet().removeIf(entry -> entry.getKey().startsWith(workspaceId + "|"));
    }

    private static String fileKey(String workspaceId, Path file) {
        return workspaceId + "|" + file.normalize();
    }

    private static boolean samePath(Path left, Path right) {
        if (left == null || right == null) return false;
        Path a = left.normalize();
        Path b = right.normalize();
        return a.equals(b) || a.endsWith(b) || b.endsWith(a);
    }
}
