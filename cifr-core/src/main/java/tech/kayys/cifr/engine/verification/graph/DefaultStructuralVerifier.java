package tech.kayys.cifr.engine.verification.graph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DefaultStructuralVerifier implements StructuralVerifier {

    @Override
    public Uni<VerificationResult> verify(Map<Path, String> modifiedFiles) {
        return Uni.createFrom().item(() -> {
            if (modifiedFiles == null || modifiedFiles.isEmpty()) {
                return new VerificationResult(
                    VerificationCheckId.of("structural.noop"),
                    VerificationStatus.SKIPPED,
                    new VerificationEvidence("ev.structural.noop", "No files modified", List.of()),
                    VerificationConfidence.HIGH
                );
            }

            List<String> errors = new ArrayList<>();

            for (Map.Entry<Path, String> entry : modifiedFiles.entrySet()) {
                String content = entry.getValue();
                String fileName = entry.getKey().getFileName().toString();

                if (fileName.endsWith(".java") || fileName.endsWith(".rs") || fileName.endsWith(".ts")) {
                    // Check balanced braces
                    int braceBalance = 0;
                    for (char c : content.toCharArray()) {
                        if (c == '{') braceBalance++;
                        if (c == '}') braceBalance--;
                    }
                    if (braceBalance != 0) {
                        errors.add("Unbalanced braces in " + fileName + " (balance: " + braceBalance + ")");
                    }

                    // Check balanced parentheses
                    int parenBalance = 0;
                    for (char c : content.toCharArray()) {
                        if (c == '(') parenBalance++;
                        if (c == ')') parenBalance--;
                    }
                    if (parenBalance != 0) {
                        errors.add("Unbalanced parentheses in " + fileName + " (balance: " + parenBalance + ")");
                    }
                }
            }

            VerificationStatus status = errors.isEmpty() ? VerificationStatus.PASSED : VerificationStatus.FAILED;
            String desc = errors.isEmpty()
                ? "Structural checks passed for " + modifiedFiles.size() + " files"
                : "Structural check failures: " + String.join("; ", errors);

            return new VerificationResult(
                VerificationCheckId.of("structural.syntax"),
                status,
                new VerificationEvidence("ev.structural.syntax", desc, errors),
                VerificationConfidence.HIGH
            );
        });
    }
}
