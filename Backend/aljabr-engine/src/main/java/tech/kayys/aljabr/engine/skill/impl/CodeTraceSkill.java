package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.reference.ReferenceRequest;
import tech.kayys.aljabr.engine.index.relation.RelatedFileRequest;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeTraceSkill implements CodingSkill {

    private final SkillContract contract;

    @Inject
    RepositoryIntelligence repositoryIntelligence;

    public CodeTraceSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.trace")
                .name("Code Trace")
                .description("Deterministic call and reference graph trace across workspace files")
                .category(SkillCategory.ANALYSIS)
                .reasoningMode(ReasoningMode.NONE)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "symbol", "string (target symbol to trace)",
                    "path", "string (optional source file path)"
                ))
                .outputSchema(Map.of(
                    "references", "array of reference occurrences",
                    "relatedFiles", "array of related files and relationships",
                    "referenceCount", "integer"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    public CodeTraceSkill(RepositoryIntelligence repositoryIntelligence) {
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
        Path filePath = pathStr != null ? Path.of(pathStr) : Path.of(".");
        SymbolId symbolId = new SymbolId(symbolName, "", "");

        ReferenceRequest refReq = new ReferenceRequest(symbolId, 10);
        RelatedFileRequest relReq = new RelatedFileRequest(filePath, 10);

        return Uni.combine().all().unis(
            repositoryIntelligence.references(refReq),
            repositoryIntelligence.related(relReq)
        ).asTuple().map(tuple -> {
            var references = tuple.getItem1();
            var related = tuple.getItem2();

            List<Map<String, Object>> refList = references.stream()
                .map(r -> Map.<String, Object>of(
                    "file", r.sourceFile().toString(),
                    "line", r.sourceRange() != null ? r.sourceRange().startLine() : 1,
                    "kind", r.kind().name()
                ))
                .collect(Collectors.toList());

            List<Map<String, Object>> relList = related.stream()
                .map(rf -> Map.<String, Object>of(
                    "file", rf.path().toString(),
                    "score", rf.score(),
                    "reasons", rf.reasons() != null ? rf.reasons().stream().map(Enum::name).collect(Collectors.toList()) : List.of()
                ))
                .collect(Collectors.toList());

            List<String> evidenceIds = new ArrayList<>();
            references.forEach(r -> evidenceIds.add(r.sourceFile().toString()));
            related.forEach(rf -> evidenceIds.add(rf.path().toString()));

            Map<String, Object> output = Map.of(
                "symbol", symbolName,
                "references", refList,
                "relatedFiles", relList,
                "referenceCount", refList.size()
            );

            return SkillResult.success(
                String.format("Traced %d references across workspace for symbol '%s'", refList.size(), symbolName),
                output,
                evidenceIds.stream().distinct().collect(Collectors.toList())
            );
        });
    }
}
