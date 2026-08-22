package tech.kayys.aljabr.engine.index.test;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.repo.ChangeRecord;
import tech.kayys.aljabr.engine.repo.ChangeSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DefaultTestDiscovery implements TestDiscovery {

    @Override
    public Uni<List<TestCandidate>> findTests(ChangeSet changeSet) {
        return Uni.createFrom().item(() -> {
            if (changeSet == null || changeSet.changes() == null || changeSet.changes().isEmpty()) {
                return List.of();
            }

            List<TestCandidate> candidates = new ArrayList<>();
            for (ChangeRecord record : changeSet.changes()) {
                String pathStr = record.path();
                if (pathStr == null || pathStr.isBlank()) {
                    continue;
                }

                Path path = Path.of(pathStr);
                String fileName = path.getFileName() != null ? path.getFileName().toString() : pathStr;

                if (fileName.endsWith("Test.java") || fileName.endsWith("Tests.java") || fileName.endsWith("_test.dart") || fileName.endsWith("Spec.scala")) {
                    candidates.add(new TestCandidate(
                        fileName,
                        path,
                        1.0,
                        List.of("Direct test file modified")
                    ));
                } else if (fileName.endsWith(".java")) {
                    String baseName = fileName.substring(0, fileName.length() - 5);
                    String testName = baseName + "Test";
                    candidates.add(new TestCandidate(
                        testName,
                        path.getParent() != null ? path.getParent().resolve(testName + ".java") : Path.of(testName + ".java"),
                        0.8,
                        List.of("Inferred test candidate for " + fileName)
                    ));
                } else if (fileName.endsWith(".dart")) {
                    String baseName = fileName.substring(0, fileName.length() - 5);
                    String testName = baseName + "_test.dart";
                    candidates.add(new TestCandidate(
                        testName,
                        path.getParent() != null ? path.getParent().resolve(testName) : Path.of(testName),
                        0.8,
                        List.of("Inferred test candidate for " + fileName)
                    ));
                }
            }

            return candidates;
        });
    }
}
