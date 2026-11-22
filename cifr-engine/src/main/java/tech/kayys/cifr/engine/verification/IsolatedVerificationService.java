package tech.kayys.cifr.engine.verification;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.verification.graph.ExecutionEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Policy boundary for executing repository-controlled verification commands.
 *
 * <p>The ordinary ProcessVerificationProvider is intentionally not used here:
 * it is a host-process adapter and does not claim OS-level isolation. This
 * service only delegates to a backend that declares every required control.</p>
 */
@ApplicationScoped
public class IsolatedVerificationService {

    private final Instance<IsolatedVerificationBackend> backends;

    @Inject
    public IsolatedVerificationService(Instance<IsolatedVerificationBackend> backends) {
        this.backends = Objects.requireNonNull(backends, "backends");
    }

    public Uni<VerificationExecutionResult> execute(VerificationCheckRequest request) {
        if (request == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("request must not be null"));
        }

        List<IsolatedVerificationBackend> eligible = new ArrayList<>();
        for (IsolatedVerificationBackend backend : backends) {
            if (backend == null || backend.backendId() == null || backend.backendId().isBlank()) {
                continue;
            }
            VerificationIsolationProfile profile = backend.isolationProfile();
            if (profile != null && profile.satisfiesStrictVerificationRequirements()) {
                eligible.add(backend);
            }
        }

        if (eligible.isEmpty()) {
            return Uni.createFrom().failure(new VerificationIsolationUnavailableException(
                    "No verification backend enforces all required isolation controls; execution refused"));
        }
        if (eligible.size() > 1) {
            return Uni.createFrom().failure(new VerificationIsolationUnavailableException(
                    "Multiple strict verification backends are available; configure an explicit backend selection"));
        }

        IsolatedVerificationBackend backend = eligible.get(0);
        return backend.execute(request)
                .onItem().ifNull().failWith(() ->
                        new IllegalStateException("Isolated verification backend returned no result"))
                .onItem().transform(result -> validateEvidence(request, result));
    }

    private static VerificationExecutionResult validateEvidence(
            VerificationCheckRequest request,
            VerificationExecutionResult result) {
        ExecutionEvidence evidence = result.evidence();
        if (!request.taskId().equals(evidence.taskId())
                || !request.workspaceId().equals(evidence.workspaceId())
                || !request.workspaceFingerprint().equals(evidence.workspaceFingerprint())
                || !request.checkId().equals(evidence.checkId())
                || !request.verifierId().equals(evidence.verifierId())
                || !request.command().equals(evidence.command())) {
            throw new IllegalStateException(
                    "Isolated verification backend returned evidence for a different request");
        }
        if (evidence.status() == null
                || evidence.startedAt() == null
                || evidence.completedAt() == null
                || evidence.completedAt().isBefore(evidence.startedAt())) {
            throw new IllegalStateException("Isolated verification backend returned invalid execution evidence");
        }
        if (evidence.status() == tech.kayys.cifr.engine.verification.graph.ExecutionCheckStatus.PASSED
                && !Integer.valueOf(0).equals(evidence.exitCode())) {
            throw new IllegalStateException("Backend reported PASSED without exit code zero");
        }
        return result;
    }
}
