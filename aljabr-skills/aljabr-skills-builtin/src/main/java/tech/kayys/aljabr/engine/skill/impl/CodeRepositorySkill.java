package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.FileIndex;
import tech.kayys.aljabr.engine.index.FileRecord;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeRepositorySkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.repository");

    private static final SkillMetadata METADATA = new SkillMetadata(
            "Repository Intelligence",
            "Answers questions about modules, packages, classes, dependencies, and architecture",
            SkillCategory.UNDERSTANDING,
            RiskLevel.LOW,
            ReasoningMode.OPTIONAL,
            false,
            Set.of("repo.graph", "repo.structure")
    );

    @Inject
    FileIndex fileIndex;

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
        Path root = context.workspaceRoot() != null ? context.workspaceRoot() : Path.of(".");
        String query = (String) context.parameters().getOrDefault("query", "");

        return fileIndex.scanWorkspace(root)
            .map(records -> {
                int totalFiles = records.size();
                long javaFiles = records.stream().filter(r -> r.path() != null && r.path().toString().endsWith(".java")).count();
                long testFiles = records.stream().filter(r -> r.path() != null && r.path().toString().contains("Test")).count();

                Set<String> packages = records.stream()
                    .map(FileRecord::path)
                    .filter(Objects::nonNull)
                    .map(Path::toString)
                    .filter(p -> p.contains("src/main/java/"))
                    .map(p -> {
                        int idx = p.indexOf("src/main/java/");
                        String sub = p.substring(idx + "src/main/java/".length());
                        int lastSlash = sub.lastIndexOf('/');
                        return lastSlash > 0 ? sub.substring(0, lastSlash).replace('/', '.') : "root";
                    })
                    .collect(Collectors.toSet());

                Map<String, Object> output = new HashMap<>();
                output.put("totalFiles", totalFiles);
                output.put("javaSourceFiles", javaFiles);
                output.put("testFiles", testFiles);
                output.put("packageCount", packages.size());
                output.put("packages", packages.stream().sorted().limit(50).toList());
                output.put("workspaceRoot", root.toString());
                output.put("query", query);

                String summary = String.format("Repository contains %d files across %d packages (%d Java sources, %d tests)",
                    totalFiles, packages.size(), javaFiles, testFiles);

                return SkillResult.success(summary, output);
            })
            .onFailure().recoverWithItem(err ->
                SkillResult.failure("Failed to build repository intelligence: " + err.getMessage()));
    }
}
