package tech.kayys.aljabr.engine.index.symbol;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.FileRecord;
import tech.kayys.aljabr.engine.index.FileIndex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultSymbolIndex implements SymbolIndex {

    private final Map<String, SymbolInfo> symbolsById = new ConcurrentHashMap<>();
    private final Map<Path, List<SymbolInfo>> symbolsByFile = new ConcurrentHashMap<>();

    @Inject
    SymbolResolver symbolResolver;
    
    @Inject
    FileIndex fileIndex;

    @Override
    public Uni<List<SymbolInfo>> getSymbols(Path file) {
        if (file != null && symbolsByFile.containsKey(file)) {
            return Uni.createFrom().item(new ArrayList<>(symbolsByFile.get(file)));
        }

        if (symbolResolver != null) {
            FileRecord mockRecord = new FileRecord(file, "mockHash", 0, "java", false, false, null);
            if (symbolResolver.supports(mockRecord)) {
                return symbolResolver.resolve(mockRecord);
            }
        }
        return Uni.createFrom().item(new ArrayList<>());
    }

    @Override
    public Uni<List<SymbolInfo>> searchSymbols(String query) {
        if (query == null || query.isBlank()) {
            return Uni.createFrom().item(new ArrayList<>(symbolsById.values()));
        }
        String lower = query.toLowerCase();
        List<SymbolInfo> matches = symbolsById.values().stream()
            .filter(s -> s.name().toLowerCase().contains(lower) ||
                         (s.id() != null && s.id().qualifiedName().toLowerCase().contains(lower)))
            .collect(Collectors.toList());
        return Uni.createFrom().item(matches);
    }

    @Override
    public Uni<SymbolInfo> getSymbol(SymbolId id) {
        if (id == null) {
            return Uni.createFrom().nullItem();
        }
        SymbolInfo info = symbolsById.get(id.qualifiedName());
        if (info == null) {
            info = symbolsById.values().stream()
                .filter(s -> s.name().equalsIgnoreCase(id.qualifiedName()) ||
                    (s.id() != null && s.id().qualifiedName().equalsIgnoreCase(id.qualifiedName())))
                .findFirst().orElse(null);
        }
        return Uni.createFrom().item(info);
    }

    @Override
    public void registerSymbol(SymbolInfo info) {
        if (info != null) {
            String key = info.id() != null ? info.id().qualifiedName() : info.name();
            symbolsById.put(key, info);
            if (info.file() != null) {
                symbolsByFile.computeIfAbsent(info.file(), k -> new ArrayList<>()).add(info);
            }
        }
    }

    @Override
    public void clear() {
        symbolsById.clear();
        symbolsByFile.clear();
    }
}
