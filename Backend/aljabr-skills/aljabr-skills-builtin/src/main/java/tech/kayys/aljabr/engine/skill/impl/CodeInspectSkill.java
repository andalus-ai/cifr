package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.symbol.SymbolIndex;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class CodeInspectSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.inspect");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Inspect",
        "Inspect source code, symbols, and dependencies relevant to a task without mutating files",
        SkillCategory.DISCOVERY,
        RiskLevel.LOW,
        ReasoningMode.OPTIONAL,
        false,
        Set.of("repo.read", "repo.symbol", "repo.search")
    );

    @Inject
    SymbolIndex symbolIndex;

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
        String rawTarget = (String) context.parameters().getOrDefault("target", "");
        if (rawTarget.isBlank()) {
            rawTarget = (String) context.parameters().getOrDefault("file", "");
        }
        if (rawTarget.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Inspection target parameter 'target' or 'file' is required"));
        }

        final String target = rawTarget;
        Path root = context.workspaceRoot() != null ? context.workspaceRoot() : Path.of(".");
        Path targetPath = root.resolve(target).normalize();

        return symbolIndex.getSymbols(targetPath)
            .map(symbols -> {
                Map<String, Object> output = new HashMap<>();
                output.put("target", target);
                output.put("absolutePath", targetPath.toString());
                output.put("exists", Files.exists(targetPath));

                if (Files.exists(targetPath)) {
                    try {
                        long lineCount = Files.lines(targetPath).count();
                        long fileSize = Files.size(targetPath);
                        output.put("lineCount", lineCount);
                        output.put("fileSizeBytes", fileSize);
                    } catch (IOException e) {
                        output.put("readError", e.getMessage());
                    }
                }

                List<Map<String, Object>> symbolList = symbols != null ? symbols.stream()
                    .map(s -> Map.<String, Object>of(
                        "name", s.name(),
                        "kind", s.kind() != null ? s.kind().name() : "UNKNOWN",
                        "range", s.range() != null ? s.range().toString() : ""
                    ))
                    .toList() : List.of();

                output.put("symbols", symbolList);
                output.put("symbolCount", symbolList.size());

                String summary = String.format("Inspected %s: %d symbols found across file", target, symbolList.size());
                return SkillResult.success(summary, output);
            })
            .onFailure().recoverWithItem(err ->
                SkillResult.failure("Failed to inspect " + target + ": " + err.getMessage()));
    }
}
