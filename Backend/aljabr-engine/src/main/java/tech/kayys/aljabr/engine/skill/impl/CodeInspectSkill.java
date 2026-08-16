package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CodeInspectSkill implements CodingSkill {

    private final SkillContract contract;

    @Inject
    RepositoryIntelligence repositoryIntelligence;

    public CodeInspectSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.inspect")
                .name("Code Inspect")
                .description("Inspect symbol declarations, structure, signature, and documentation in workspace")
                .category(SkillCategory.ANALYSIS)
                .reasoningMode(ReasoningMode.OPTIONAL)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "symbol", "string (fully qualified or simple symbol name)",
                    "path", "string (optional source file path)"
                ))
                .outputSchema(Map.of(
                    "symbol", "object (symbol structure details)",
                    "found", "boolean"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    public CodeInspectSkill(RepositoryIntelligence repositoryIntelligence) {
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
        if (symbolName == null || symbolName.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Missing required parameter: 'symbol'"));
        }

        String pathStr = (String) context.parameters().get("path");
        String signature = (String) context.parameters().getOrDefault("signature", "");
        SymbolId symbolId = new SymbolId(symbolName, signature, "");

        return repositoryIntelligence.symbol(symbolId)
            .map(info -> {
                if (info == null) {
                    return SkillResult.failure("Symbol not found: " + symbolName, Map.of("found", false));
                }

                Map<String, Object> output = Map.of(
                    "found", true,
                    "name", info.name(),
                    "kind", info.kind().name(),
                    "file", info.file() != null ? info.file().toString() : (pathStr != null ? pathStr : ""),
                    "lineStart", info.range() != null ? info.range().startLine() : 1,
                    "lineEnd", info.range() != null ? info.range().endLine() : 1
                );

                List<String> evidenceIds = info.file() != null ? List.of(info.file().toString()) : List.of();

                return SkillResult.success(
                    String.format("Inspected %s '%s' in %s", info.kind(), info.name(), info.file()),
                    output,
                    evidenceIds
                );
            });
    }
}
