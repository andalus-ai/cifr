package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.dependency.DependencyRequest;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeExplainSkill implements CodingSkill {

    private final SkillContract contract;

    @Inject
    RepositoryIntelligence repositoryIntelligence;

    public CodeExplainSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.explain")
                .name("Code Explain")
                .description("Explain code semantics, architectural purpose, and dependencies of a symbol or snippet")
                .category(SkillCategory.EXPLANATION)
                .reasoningMode(ReasoningMode.REQUIRED)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "symbol", "string (optional symbol name to explain)",
                    "path", "string (optional source file path)",
                    "snippet", "string (optional code snippet)"
                ))
                .outputSchema(Map.of(
                    "explanationContext", "object containing code facts, symbols, and dependencies",
                    "requiresModel", "boolean"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    public CodeExplainSkill(RepositoryIntelligence repositoryIntelligence) {
        this();
        this.repositoryIntelligence = repositoryIntelligence;
    }

    @Override
    public SkillContract contract() {
        return contract;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        String symbolName = (String) context.parameters().get("symbol");
        String pathStr = (String) context.parameters().get("path");
        String snippet = (String) context.parameters().get("snippet");

        if ((symbolName == null || symbolName.isBlank()) && (snippet == null || snippet.isBlank()) && (pathStr == null || pathStr.isBlank())) {
            return Uni.createFrom().item(SkillResult.failure("Must provide at least 'symbol', 'path', or 'snippet'"));
        }

        Path filePath = pathStr != null ? Path.of(pathStr) : Path.of(".");
        List<String> evidenceIds = new ArrayList<>();
        if (pathStr != null) evidenceIds.add(pathStr);

        DependencyRequest depReq = new DependencyRequest(filePath.toString(), null);

        return repositoryIntelligence.dependencies(depReq)
            .flatMap(deps -> {
                List<Map<String, Object>> depList = deps.stream()
                    .map(d -> Map.<String, Object>of(
                        "source", d.source() != null ? d.source() : "",
                        "target", d.target() != null ? d.target() : "",
                        "kind", d.kind() != null ? d.kind().name() : ""
                    ))
                    .collect(Collectors.toList());

                if (symbolName != null && !symbolName.isBlank()) {
                    SymbolId symbolId = new SymbolId(symbolName, "", "");
                    return repositoryIntelligence.symbol(symbolId).map(symInfo -> {
                        Map<String, Object> facts = new HashMap<>();
                        facts.put("symbol", symbolName);
                        facts.put("symbolFound", symInfo != null);
                        facts.put("kind", symInfo != null ? symInfo.kind().name() : "UNKNOWN");
                        facts.put("file", filePath.toString());
                        facts.put("dependencies", depList);
                        facts.put("snippet", snippet != null ? snippet : "");

                        return SkillResult.success(
                            "Context prepared for model explanation: " + symbolName,
                            Map.of("explanationContext", facts, "requiresModel", true),
                            evidenceIds
                        );
                    });
                } else {
                    Map<String, Object> facts = new HashMap<>();
                    facts.put("file", filePath.toString());
                    facts.put("dependencies", depList);
                    facts.put("snippet", snippet != null ? snippet : "");

                    return Uni.createFrom().item(SkillResult.success(
                        "Context prepared for model explanation: " + filePath,
                        Map.of("explanationContext", facts, "requiresModel", true),
                        evidenceIds
                    ));
                }
            });
    }
}
