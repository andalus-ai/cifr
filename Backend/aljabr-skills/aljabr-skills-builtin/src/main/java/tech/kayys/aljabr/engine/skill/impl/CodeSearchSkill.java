package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.search.SearchMatch;
import tech.kayys.aljabr.engine.index.search.SearchRequest;
import tech.kayys.aljabr.engine.index.search.SearchScope;
import tech.kayys.aljabr.engine.index.search.TextSearchEngine;
import tech.kayys.aljabr.engine.index.symbol.SymbolIndex;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class CodeSearchSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.search");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Search",
        "Deterministic symbol, reference, and text search across the codebase",
        SkillCategory.DISCOVERY,
        RiskLevel.LOW,
        ReasoningMode.NONE,
        false,
        Set.of("repo.search", "repo.symbol")
    );

    @Inject
    TextSearchEngine textSearchEngine;

    @Inject
    SymbolIndex symbolIndex;

    public CodeSearchSkill() {}

    public CodeSearchSkill(TextSearchEngine textSearchEngine, SymbolIndex symbolIndex) {
        this.textSearchEngine = textSearchEngine;
        this.symbolIndex = symbolIndex;
    }

    @Override
    public SkillId id() {
        return ID;
    }

    @Override
    public SkillMetadata metadata() {
        return METADATA;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        String query = (String) context.parameters().getOrDefault("query", "");
        if (query.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Search query parameter 'query' cannot be empty"));
        }

        Path root = context.workspaceRoot() != null ? context.workspaceRoot() : Path.of(".");
        int limit = ((Number) context.parameters().getOrDefault("limit", 20)).intValue();

        Uni<List<Map<String, Object>>> symbolsUni = (symbolIndex != null)
            ? symbolIndex.searchSymbols(query)
                .map(symbols -> symbols.stream()
                    .map(s -> Map.<String, Object>of(
                        "name", s.name(),
                        "kind", s.kind() != null ? s.kind().name() : "UNKNOWN",
                        "file", s.file() != null ? s.file().toString() : ""
                    ))
                    .toList())
                .onFailure().recoverWithItem(List.of())
            : Uni.createFrom().item(List.of());

        SearchRequest searchReq = new SearchRequest(query, new SearchScope(List.of(), List.of()), limit, List.of());
        Uni<List<Map<String, Object>>> textMatchesUni = (textSearchEngine != null)
            ? textSearchEngine.search(searchReq, root)
                .map(res -> {
                    if (res == null || res.matches() == null) return List.<Map<String, Object>>of();
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (SearchMatch m : res.matches()) {
                        list.add(Map.of(
                            "path", m.file() != null ? m.file().toString() : "",
                            "lineNumber", m.lineNumber(),
                            "snippet", m.snippet() != null ? m.snippet() : "",
                            "score", m.score()
                        ));
                    }
                    return list;
                })
                .onFailure().recoverWithItem(List.of())
            : Uni.createFrom().item(List.of());

        return Uni.combine().all().unis(symbolsUni, textMatchesUni).asTuple()
            .map(tuple -> {
                List<Map<String, Object>> symbols = tuple.getItem1();
                List<Map<String, Object>> textMatches = tuple.getItem2();

                int totalCount = symbols.size() + textMatches.size();
                String summary = String.format("Search completed for query: %s (%d total matches)", query, totalCount);

                Map<String, Object> output = new HashMap<>();
                output.put("query", query);
                output.put("symbols", symbols);
                output.put("matches", textMatches);
                output.put("totalCount", totalCount);

                return SkillResult.success(summary, output);
            });
    }
}
