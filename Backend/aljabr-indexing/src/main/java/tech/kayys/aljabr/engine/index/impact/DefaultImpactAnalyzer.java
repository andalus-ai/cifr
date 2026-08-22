package tech.kayys.aljabr.engine.index.impact;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.FileIndex;
import tech.kayys.aljabr.engine.index.dependency.DependencyIndex;
import tech.kayys.aljabr.engine.index.dependency.DependencyRequest;
import tech.kayys.aljabr.engine.index.relation.RelatedFile;
import tech.kayys.aljabr.engine.index.relation.RelatedFileRequest;
import tech.kayys.aljabr.engine.index.relation.RelationIndex;
import tech.kayys.aljabr.engine.repo.ChangeRecord;
import tech.kayys.aljabr.engine.repo.ChangeSet;

import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class DefaultImpactAnalyzer implements ImpactAnalyzer {

    @Inject
    RelationIndex relationIndex;

    @Inject
    DependencyIndex dependencyIndex;

    @Inject
    FileIndex fileIndex;

    public DefaultImpactAnalyzer() {}

    public DefaultImpactAnalyzer(RelationIndex relationIndex, DependencyIndex dependencyIndex, FileIndex fileIndex) {
        this.relationIndex = relationIndex;
        this.dependencyIndex = dependencyIndex;
        this.fileIndex = fileIndex;
    }

    @Override
    public Uni<ImpactReport> analyze(ChangeSet changeSet) {
        return Uni.createFrom().item(() -> {
            if (changeSet == null || changeSet.changes() == null || changeSet.changes().isEmpty()) {
                return new ImpactReport(List.of(), List.of(), List.of(), ImpactConfidence.LOW);
            }

            List<ImpactNode> affectedFiles = new ArrayList<>();
            List<Path> likelyTests = new ArrayList<>();

            for (ChangeRecord change : changeSet.changes()) {
                if (change.path() == null || change.path().isBlank()) {
                    continue;
                }

                Path path = Path.of(change.path());
                String fileName = path.getFileName() != null ? path.getFileName().toString() : change.path();

                affectedFiles.add(new ImpactNode(
                    change.path(),
                    "FILE",
                    path,
                    1.0,
                    List.of("Directly modified file: " + change.type())
                ));

                if (fileName.endsWith("Test.java") || fileName.endsWith("Tests.java") || fileName.endsWith("_test.dart")) {
                    likelyTests.add(path);
                } else if (fileName.endsWith(".java")) {
                    String testName = fileName.substring(0, fileName.length() - 5) + "Test.java";
                    Path testPath = path.getParent() != null ? path.getParent().resolve(testName) : Path.of(testName);
                    likelyTests.add(testPath);
                }
            }

            return new ImpactReport(
                List.of(),
                affectedFiles,
                likelyTests,
                affectedFiles.isEmpty() ? ImpactConfidence.LOW : ImpactConfidence.HIGH
            );
        });
    }
}
