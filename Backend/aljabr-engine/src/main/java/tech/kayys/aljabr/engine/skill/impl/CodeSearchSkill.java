package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.search.SearchRequest;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeSearchSkill implements CodingSkill {

    private final SkillContract contract;

    @Inject
    RepositoryIntelligence repositoryIntelligence;

    public CodeSearchSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.search")
                .name("Code Search")
                .description("Deterministic code and symbol search using repository intelligence index and LSP")
                .category(SkillCategory.DISCOVERY)
                .reasoningMode(ReasoningMode.NONE)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "query", "string (search query or symbol name)",
                    "type", "string (EXACT, FUZZY, SYMBOL, SEMANTIC)",
                    "limit", "integer (max results, default 10)"
                ))
                .outputSchema(Map.of(
                    "matches", "array of search match items",
                    "totalCount", "integer"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    public CodeSearchSkill(RepositoryIntelligence repositoryIntelligence) {
        this();
        this.repositoryIntelligence = repositoryIntelligence;
    }

    @Override
    public SkillContract contract() {
        return contract;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        String query = (String) context.parameters().get("query");
        if (query == null || query.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Missing required parameter: 'query'"));
        }

        int limit = 10;
        Object limitObj = context.parameters().get("limit");
        if (limitObj instanceof Number num) {
            limit = num.intValue();
        }

        SearchRequest request = new SearchRequest(query, null, limit, List.of());
        Path root = context.workspaceRoot() != null ? context.workspaceRoot() : Path.of(".");
        String workspaceId = context.taskId() != null ? context.taskId() : "default";

        return repositoryIntelligence.search(request, root, workspaceId)
            .map(searchResult -> {
                List<Map<String, Object>> matches = searchResult.matches().stream()
                    .map(item -> Map.<String, Object>of(
                        "path", item.file().toString(),
                        "lineNumber", item.lineNumber(),
                        "score", item.score(),
                        "summary", item.snippet() != null ? item.snippet() : ""
                    ))
                    .collect(Collectors.toList());

                List<String> evidenceIds = searchResult.matches().stream()
                    .map(item -> item.file().toString())
                    .collect(Collectors.toList());

                return SkillResult.success(
                    String.format("Found %d matches for query '%s'", matches.size(), query),
                    Map.of("matches", matches, "totalCount", matches.size(), "query", query),
                    evidenceIds
                );
            });
    }
}
