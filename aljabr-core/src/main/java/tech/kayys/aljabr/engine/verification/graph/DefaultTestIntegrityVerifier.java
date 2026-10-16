package tech.kayys.aljabr.engine.verification.graph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DefaultTestIntegrityVerifier implements TestIntegrityVerifier {

    @Override
    public Uni<VerificationResult> verifyIntegrity(Map<Path, String> modifiedFiles) {
        return Uni.createFrom().item(() -> {
            if (modifiedFiles == null || modifiedFiles.isEmpty()) {
                return new VerificationResult(
                    VerificationCheckId.of("test.integrity.noop"),
                    VerificationStatus.SKIPPED,
                    new VerificationEvidence("ev.test.integrity.noop", "No files modified", List.of()),
                    VerificationConfidence.HIGH
                );
            }

            List<String> integrityIssues = new ArrayList<>();

            for (Map.Entry<Path, String> entry : modifiedFiles.entrySet()) {
                String pathStr = entry.getKey().toString().toLowerCase();
                String content = entry.getValue();

                // If test file modified, check for suspicious bypass patterns
                if (pathStr.contains("test") || pathStr.contains("spec")) {
                    if (content.contains("@Disabled") || content.contains("@Ignore") || content.contains(".skip(")) {
                        integrityIssues.add("Disabled/skipped test annotation detected in " + entry.getKey().getFileName());
                    }
                    if (content.contains("assertTrue(true)") || content.contains("assert(true)")) {
                        integrityIssues.add("Tautological assertion (assertTrue(true)) detected in " + entry.getKey().getFileName());
                    }
                }
            }

            VerificationStatus status = integrityIssues.isEmpty() ? VerificationStatus.PASSED : VerificationStatus.FAILED;
            String description = integrityIssues.isEmpty()
                ? "Test integrity verified: no bypassed or tautological assertions detected"
                : "Test integrity compromised: " + String.join("; ", integrityIssues);

            return new VerificationResult(
                VerificationCheckId.of("test.integrity"),
                status,
                new VerificationEvidence("ev.test.integrity", description, integrityIssues),
                VerificationConfidence.HIGH
            );
        });
    }
}
