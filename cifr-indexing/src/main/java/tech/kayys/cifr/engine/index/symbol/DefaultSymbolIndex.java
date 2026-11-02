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
        if (file == null || workspaceId == null || workspaceId.isBlank()) {
            return Uni.createFrom().item(List.of());
        }
        String key = fileKey(workspaceId, file);
        List<SymbolInfo> cached = symbolsByFile.get(key);
        if (fileIndex == null || symbolResolver == null) {
            return Uni.createFrom().item(cached == null ? List.of() : new ArrayList<>(cached));
        }
        return fileIndex.getCurrentIndex(workspaceId).onItem().transformToUni(records -> {
            FileRecord record = records.stream()
                    .filter(r -> samePath(r.path(), file))
                    .findFirst().orElse(null);
            if (record == null || !symbolResolver.supports(record)) {
                return Uni.createFrom().item(cached == null ? List.of() : new ArrayList<>(cached));
            }
            return symbolResolver.resolve(record).onItem().invoke(found -> {
                if (found != null) found.forEach(this::registerSymbol);
            }).onItem().transform(found -> found == null || found.isEmpty()
                    ? (cached == null ? List.of() : new ArrayList<>(cached))
                    : found);
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
        if (id == null) return Uni.createFrom().nullItem();
        SymbolInfo info = symbolsById.values().stream()
                .filter(s -> s.id() != null && s.id().equals(id))
                .findFirst().orElseGet(() -> symbolsById.values().stream()
                        .filter(s -> s.name().equalsIgnoreCase(id.qualifiedName())
                                || (s.id() != null && s.id().qualifiedName().equalsIgnoreCase(id.qualifiedName())))
                        .findFirst().orElse(null));
        return Uni.createFrom().item(info);
    }

    @Override
    public Uni<SymbolInfo> getSymbol(SymbolId id, String workspaceId) {
        if (id == null) return Uni.createFrom().nullItem();
        SymbolInfo info = symbolsById.values().stream()
                .filter(s -> workspaceId == null || workspaceId.equals(s.workspaceId()))
                .filter(s -> s.id() != null && s.id().equals(id))
                .findFirst().orElseGet(() -> symbolsById.values().stream()
                        .filter(s -> workspaceId == null || workspaceId.equals(s.workspaceId()))
                        .filter(s -> s.name().equalsIgnoreCase(id.qualifiedName())
                                || (s.id() != null && s.id().qualifiedName().equalsIgnoreCase(id.qualifiedName())))
                        .findFirst().orElse(null));
        return Uni.createFrom().item(info);
    }

    @Override
    public void registerSymbol(SymbolInfo info) {
        if (info == null) return;
        String qualified = info.id() == null ? info.name() : info.id().qualifiedName();
        String key = info.workspaceId() + "|" + (info.file() == null ? "" : info.file()) + "|" + qualified;
        SymbolInfo previous = symbolsById.put(key, info);
        if (info.file() != null) {
            CopyOnWriteArrayList<SymbolInfo> fileSymbols =
                    symbolsByFile.computeIfAbsent(fileKey(info.workspaceId(), info.file()), ignored -> new CopyOnWriteArrayList<>());
            if (previous != null) fileSymbols.remove(previous);
            fileSymbols.addIfAbsent(info);
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

    @Override
    public void invalidateResolverCache(String workspaceId) {
        if (symbolResolver != null) symbolResolver.invalidateWorkspace(workspaceId);
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
