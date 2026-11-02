package tech.kayys.cifr.engine.index;

import tech.kayys.cifr.engine.index.reference.Reference;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;

import java.util.List;

/**
 * Unified query service for repository intelligence.
 * Provides a stable API over FileIndex, SymbolIndex, ReferenceIndex, TestIndex, and ImpactAnalyzer.
 *
 * Phase handlers and tools inject this instead of individual indexes directly.
 */
public interface RepositoryQueryService {

    List<RankedResult<SymbolInfo>> findSymbols(SymbolQuery query);

    List<RankedResult<Reference>> findReferences(ReferenceQuery query);

    List<RankedResult<FileRecord>> findFiles(FileQuery query);

    List<FileImportance> rankFiles(String taskDescription, List<String> fileIds);
}
