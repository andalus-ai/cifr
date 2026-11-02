package tech.kayys.cifr.engine.verification.graph;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.Map;

public interface TestIntegrityVerifier {

    Uni<VerificationResult> verifyIntegrity(Map<Path, String> modifiedFiles);
}
