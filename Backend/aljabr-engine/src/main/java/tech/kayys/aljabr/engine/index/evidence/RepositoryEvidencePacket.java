package tech.kayys.aljabr.engine.index.evidence;

import java.util.List;

public record RepositoryEvidencePacket(
    List<String> files,
    List<String> symbols,
    List<String> relations,
    List<String> tests,
    String summary
) {
    public RepositoryEvidencePacket {
        files = files != null ? List.copyOf(files) : List.of();
        symbols = symbols != null ? List.copyOf(symbols) : List.of();
        relations = relations != null ? List.copyOf(relations) : List.of();
        tests = tests != null ? List.copyOf(tests) : List.of();
    }
}
