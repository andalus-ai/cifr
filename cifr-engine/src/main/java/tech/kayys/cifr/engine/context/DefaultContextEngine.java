package tech.kayys.cifr.engine.context;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.FileIndex;
import tech.kayys.cifr.engine.index.dependency.DependencyIndex;
import tech.kayys.cifr.engine.index.relation.RelationIndex;
import tech.kayys.cifr.engine.index.symbol.SymbolIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link ContextEngine}.
 *
 * <p>Assembles model-facing context packets from:
 * <ul>
 *   <li>Task objective and constraints from the {@link ContextPlan}</li>
 *   <li>Target symbol declarations looked up from the {@link SymbolIndex}</li>
 *   <li>File source snippets read from workspace</li>
 *   <li>Failure/error traces</li>
 *   <li>Dependency relationships from {@link DependencyIndex} and {@link RelationIndex}</li>
 * </ul>
 * Each section is passed through the {@link ContextBudgeter} to ensure the total fits the model's token budget.
 */
@ApplicationScoped
public class DefaultContextEngine implements ContextEngine {

    @Inject
    SymbolIndex symbolIndex;

    @Inject
    FileIndex fileIndex;

    @Inject
    DependencyIndex dependencyIndex;

    @Inject
    RelationIndex relationIndex;

    @Inject
    ContextBudgeter budgeter;

    public DefaultContextEngine() {}

    public DefaultContextEngine(SymbolIndex symbolIndex,
                                FileIndex fileIndex,
                                DependencyIndex dependencyIndex,
                                RelationIndex relationIndex,
                                ContextBudgeter budgeter) {
        this.symbolIndex = symbolIndex;
        this.fileIndex = fileIndex;
        this.dependencyIndex = dependencyIndex;
        this.relationIndex = relationIndex;
        this.budgeter = budgeter;
    }

    @Override
    public Uni<ContextPacket> assemble(ContextPlan plan, Path workspaceRoot, String projectId) {
        return Uni.createFrom().item(() -> doAssemble(plan, workspaceRoot));
    }

    private ContextPacket doAssemble(ContextPlan plan, Path workspaceRoot) {
        ContextBudget budget = plan.budget();
        List<ContextSection> sections = new ArrayList<>();

        // 1. Task objective
        ContextSection taskSection = ContextSection.of("TASK OBJECTIVE", plan.taskDescription(), 100);
        sections.add(budgeter.budgetSection(taskSection, budget.taskBudgetTokens()));

        // 2. Failure / error trace (if provided)
        plan.failureTrace().ifPresent(trace -> {
            ContextSection traceSection = ContextSection.of("FAILURE / ERROR TRACE", trace, 80);
            sections.add(budgeter.budgetSection(traceSection, budget.failureBudgetTokens()));
        });

        // 3. Target symbol declaration (from index)
        plan.targetSymbol().ifPresent(symbol -> {
            var searchResults = symbolIndex.searchSymbols(symbol).await().indefinitely();
            if (searchResults != null && !searchResults.isEmpty()) {
                var info = searchResults.get(0);
                String decl = "Symbol: " + info.name() + " [" + info.kind().name() + "]\n"
                    + "File: " + info.file() + "\n"
                    + "Range: L" + info.range().startLine() + "-L" + info.range().endLine();
                ContextSection declSection = ContextSection.of("TARGET DECLARATION", decl, 90);
                sections.add(budgeter.budgetSection(declSection, budget.targetBudgetTokens()));
            }
        });

        // 4. Source snippet from target file
        plan.targetFile().ifPresent(relPath -> {
            Path absPath = workspaceRoot.resolve(relPath);
            if (Files.exists(absPath)) {
                try {
                    String source = Files.readString(absPath);
                    ContextSection snippetSection = ContextSection.of("CODE SNIPPET", source, 70);
                    sections.add(budgeter.budgetSection(snippetSection, budget.snippetBudgetTokens()));
                } catch (IOException ignored) {
                    // best-effort: skip if unreadable
                }
            }
        });

        return ContextPacket.of(plan.taskId(), sections);
    }
}
