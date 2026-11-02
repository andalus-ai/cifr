package tech.kayys.cifr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class DefaultTransactionalPatchEngine implements TransactionalPatchEngine {

    @Inject
    ChangePrecondition precondition;

    @Override
    public Uni<PatchResult> applyTransactional(
        SemanticChangeIntent intent,
        List<EditOperation> operations,
        Path workspaceRoot,
        ChangeBudget budget
    ) {
        ChangeBudget actualBudget = budget != null ? budget : ChangeBudget.defaultBudget();

        return precondition.validate(intent, operations, workspaceRoot).flatMap(validation -> {
            if (!validation.isValid()) {
                return Uni.createFrom().item(new PatchResult(
                    PatchStatus.PRECONDITION_FAILED,
                    Map.of(),
                    0,
                    validation.errors()
                ));
            }

            WorkspaceOverlay overlay = new InMemoryWorkspaceOverlay(workspaceRoot);
            Set<Path> affectedFiles = new HashSet<>();
            int totalLinesChanged = 0;

            try {
                // Group operations by file
                Map<Path, List<EditOperation>> opsByFile = new HashMap<>();
                for (EditOperation op : operations) {
                    Path resolved = workspaceRoot != null && !op.file().isAbsolute()
                        ? workspaceRoot.resolve(op.file())
                        : op.file();
                    opsByFile.computeIfAbsent(resolved, k -> new ArrayList<>()).add(op);
                    affectedFiles.add(resolved);
                }

                if (affectedFiles.size() > actualBudget.maxFiles()) {
                    return Uni.createFrom().item(new PatchResult(
                        PatchStatus.BUDGET_EXCEEDED,
                        Map.of(),
                        0,
                        List.of("Modified files count " + affectedFiles.size() + " exceeds budget " + actualBudget.maxFiles())
                    ));
                }

                for (Map.Entry<Path, List<EditOperation>> entry : opsByFile.entrySet()) {
                    Path file = entry.getKey();
                    List<String> lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file)) : new ArrayList<>();

                    for (EditOperation op : entry.getValue()) {
                        if (op instanceof EditOperation.ReplaceRange r) {
                            int start = Math.max(0, r.startLine() - 1);
                            int end = Math.min(lines.size(), r.endLine());
                            for (int i = end - 1; i >= start; i--) {
                                if (i < lines.size()) lines.remove(i);
                            }
                            lines.add(start, r.replacement());
                            totalLinesChanged += (r.endLine() - r.startLine() + 1);
                        } else if (op instanceof EditOperation.InsertBefore ib) {
                            int lineIdx = Math.min(lines.size(), Math.max(0, ib.line() - 1));
                            lines.add(lineIdx, ib.content());
                            totalLinesChanged++;
                        } else if (op instanceof EditOperation.InsertAfter ia) {
                            int lineIdx = Math.min(lines.size(), Math.max(0, ia.line()));
                            lines.add(lineIdx, ia.content());
                            totalLinesChanged++;
                        } else if (op instanceof EditOperation.DeleteRange dr) {
                            int start = Math.max(0, dr.startLine() - 1);
                            int end = Math.min(lines.size(), dr.endLine());
                            for (int i = end - 1; i >= start; i--) {
                                if (i < lines.size()) lines.remove(i);
                            }
                            totalLinesChanged += (dr.endLine() - dr.startLine() + 1);
                        } else if (op instanceof EditOperation.AddImport ai) {
                            int insertIdx = 0;
                            for (int i = 0; i < lines.size(); i++) {
                                if (lines.get(i).startsWith("package ")) {
                                    insertIdx = i + 1;
                                } else if (lines.get(i).startsWith("import ")) {
                                    insertIdx = i + 1;
                                }
                            }
                            lines.add(insertIdx, ai.importStatement());
                            totalLinesChanged++;
                        }
                    }

                    overlay.stage(file, String.join("\n", lines)).await().indefinitely();
                }

                if (totalLinesChanged > actualBudget.maxChangedLines()) {
                    overlay.rollback().await().indefinitely();
                    return Uni.createFrom().item(new PatchResult(
                        PatchStatus.BUDGET_EXCEEDED,
                        Map.of(),
                        totalLinesChanged,
                        List.of("Total changed lines " + totalLinesChanged + " exceeds budget " + actualBudget.maxChangedLines())
                    ));
                }

                // Commit overlay
                overlay.commit().await().indefinitely();

                return Uni.createFrom().item(new PatchResult(
                    PatchStatus.APPLIED,
                    overlay.stagedChanges(),
                    totalLinesChanged,
                    List.of()
                ));

            } catch (Exception e) {
                overlay.rollback().await().indefinitely();
                return Uni.createFrom().item(new PatchResult(
                    PatchStatus.VALIDATION_FAILED,
                    Map.of(),
                    totalLinesChanged,
                    List.of("Patch application failed: " + e.getMessage())
                ));
            }
        });
    }
}
