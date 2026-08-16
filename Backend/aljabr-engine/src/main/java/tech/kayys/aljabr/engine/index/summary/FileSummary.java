package tech.kayys.aljabr.engine.index.summary;

import java.nio.file.Path;
import java.util.List;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;

public record FileSummary(
    Path file,
    String summary,
    List<String> responsibilities,
    List<SymbolId> keySymbols,
    String sourceHash
) {
    public FileSummary {
        responsibilities = responsibilities != null ? List.copyOf(responsibilities) : List.of();
        keySymbols = keySymbols != null ? List.copyOf(keySymbols) : List.of();
    }
}
