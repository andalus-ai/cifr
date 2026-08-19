package tech.kayys.aljabr.engine.verification.graph;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.Map;

public interface StructuralVerifier {

    Uni<VerificationResult> verify(Map<Path, String> modifiedFiles);
}
