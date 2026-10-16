package tech.kayys.aljabr.engine.index.symbol;

import tech.kayys.aljabr.engine.index.reference.Reference;
import tech.kayys.aljabr.engine.index.test.TestCandidate;

import java.util.List;

/**
 * Result of parsing a single source file.
 * Provides a normalized view of symbols, references, imports, and tests
 * for downstream index population.
 */
public record ParsedFile(
    String fileId,
    String filePath,
    List<SymbolInfo> symbols,
    List<Reference> references,
    List<Import> imports,
    List<TestCandidate> tests
) {
    public ParsedFile {
        symbols = symbols != null ? List.copyOf(symbols) : List.of();
        references = references != null ? List.copyOf(references) : List.of();
        imports = imports != null ? List.copyOf(imports) : List.of();
        tests = tests != null ? List.copyOf(tests) : List.of();
    }

    public static ParsedFile empty(String fileId, String filePath) {
        return new ParsedFile(fileId, filePath, List.of(), List.of(), List.of(), List.of());
    }
}
