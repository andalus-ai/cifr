package tech.kayys.aljabr.engine.context;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.index.FileIndex;
import tech.kayys.aljabr.engine.index.dependency.Dependency;
import tech.kayys.aljabr.engine.index.dependency.DependencyIndex;
import tech.kayys.aljabr.engine.index.dependency.DependencyRequest;
import tech.kayys.aljabr.engine.index.relation.RelatedFile;
import tech.kayys.aljabr.engine.index.relation.RelatedFileRequest;
import tech.kayys.aljabr.engine.index.relation.RelationIndex;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.index.symbol.SymbolIndex;
import tech.kayys.aljabr.engine.index.symbol.SymbolInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultContextEngine implements ContextEngine {

    private static final Logger LOG = Logger.getLogger(DefaultContextEngine.class);

    @Inject
    SymbolIndex symbolIndex;

    @Inject
    FileIndex fileIndex;

    @Inject
    DependencyIndex dependencyIndex;

    @Inject
    RelationIndex relationIndex;

    @Inject
    ContextBudgeter contextBudgeter;

    public DefaultContextEngine() {
    }

    public DefaultContextEngine(SymbolIndex symbolIndex, FileIndex fileIndex, DependencyIndex dependencyIndex, RelationIndex relationIndex, ContextBudgeter contextBudgeter) {
        this.symbolIndex = symbolIndex;
        this.fileIndex = fileIndex;
        this.dependencyIndex = dependencyIndex;
        this.relationIndex = relationIndex;
        this.contextBudgeter = contextBudgeter;
    }

    @Override
    public Uni<ContextPacket> assemble(ContextPlan plan, Path workspaceRoot, String workspaceId) {
        return Uni.createFrom().item(() -> {
            if (plan == null) {
                return ContextPacket.of("empty", List.of());
            }

            Path root = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
            List<ContextSection> rawSections = new ArrayList<>();

            // 1. Task Section (Priority 100)
            rawSections.add(ContextSection.of("TASK OBJECTIVE", plan.taskDescription(), 100));

            // 2. Failure Trace (Priority 90)
            plan.failureTrace().ifPresent(trace -> {
                if (!trace.isBlank()) {
                    rawSections.add(ContextSection.of("FAILURE / ERROR TRACE", trace, 90));
                }
            });

            // 3. Target Symbol & Code Snippet (Priority 80 & 70)
            if (plan.targetSymbol().isPresent()) {
                String symName = plan.targetSymbol().get();
                SymbolInfo symbol = null;
                if (symbolIndex != null) {
                    symbol = symbolIndex.getSymbol(new SymbolId(symName, "", "")).await().indefinitely();
                }

                if (symbol != null) {
                    String symDesc = String.format("Name: %s (%s)\nFile: %s\nRange: Line %d-%d",
                        symbol.name(), symbol.kind(), symbol.file(),
                        symbol.range() != null ? symbol.range().startLine() : 1,
                        symbol.range() != null ? symbol.range().endLine() : 1);
                    rawSections.add(ContextSection.of("TARGET DECLARATION", symDesc, 80));

                    // Extract code snippet from file
                    if (symbol.file() != null) {
                        Path fullPath = root.resolve(symbol.file()).normalize();
                        String snippet = readSnippet(fullPath, symbol.range() != null ? symbol.range().startLine() : 1, 40);
                        if (!snippet.isBlank()) {
                            rawSections.add(ContextSection.of("CODE SNIPPET (" + symbol.file() + ")", snippet, 70));
                        }
                    }
                } else {
                    rawSections.add(ContextSection.of("TARGET SYMBOL", "Target symbol requested: " + symName, 80));
                }
            } else if (plan.targetFile().isPresent()) {
                Path targetFile = plan.targetFile().get();
                Path fullPath = root.resolve(targetFile).normalize();
                String snippet = readSnippet(fullPath, 1, 60);
                if (!snippet.isBlank()) {
                    rawSections.add(ContextSection.of("TARGET FILE CONTENT (" + targetFile + ")", snippet, 70));
                }
            }

            // 4. Dependencies & Related Tests (Priority 50)
            if (plan.targetSymbol().isPresent() && dependencyIndex != null) {
                String sym = plan.targetSymbol().get();
                List<Dependency> deps = dependencyIndex.getDependencies(new DependencyRequest(sym, null)).await().indefinitely();
                if (deps != null && !deps.isEmpty()) {
                    String depList = deps.stream().map(d -> "- " + d.target() + " (" + d.kind() + ")").collect(Collectors.joining("\n"));
                    rawSections.add(ContextSection.of("DEPENDENCIES", depList, 50));
                }
            }

            if (plan.targetFile().isPresent() && relationIndex != null) {
                List<RelatedFile> rels = relationIndex.getRelatedFiles(new RelatedFileRequest(plan.targetFile().get(), 5)).await().indefinitely();
                if (rels != null && !rels.isEmpty()) {
                    String relList = rels.stream().map(r -> "- " + r.path() + " (" + r.reasons() + ")").collect(Collectors.joining("\n"));
                    rawSections.add(ContextSection.of("RELATED FILES & TESTS", relList, 50));
                }
            }

            // 5. Budget enforcement
            List<ContextSection> budgeted = contextBudgeter != null ?
                contextBudgeter.budgetAll(rawSections, plan.budget()) : rawSections;

            return ContextPacket.of(plan.taskId(), budgeted);
        });
    }

    private String readSnippet(Path filePath, int startLine, int windowSize) {
        if (!Files.exists(filePath)) return "";
        try {
            List<String> allLines = Files.readAllLines(filePath);
            int start = Math.max(0, startLine - 10);
            int end = Math.min(allLines.size(), start + windowSize);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(String.format("%4d | %s\n", i + 1, allLines.get(i)));
            }
            return sb.toString();
        } catch (IOException e) {
            LOG.warnf("Could not read snippet from %s: %s", filePath, e.getMessage());
            return "";
        }
    }
}
