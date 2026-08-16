package tech.kayys.aljabr.engine.patch;

import java.nio.file.Path;
import java.util.List;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;

public record PlannedChange(
    Path path,
    ChangeIntent intent,
    List<SymbolId> affectedSymbols,
    List<String> evidenceIds
) {
    public PlannedChange {
        affectedSymbols = affectedSymbols != null ? List.copyOf(affectedSymbols) : List.of();
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
