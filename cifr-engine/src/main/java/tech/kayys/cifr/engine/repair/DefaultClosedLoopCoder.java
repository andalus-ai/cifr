package tech.kayys.cifr.engine.repair;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.context.ContextPlan;

import java.nio.file.Path;
import java.util.List;

/**
 * Default closed-loop repair entry point.
 *
 * <p>This implementation currently has no model/planner input capable of
 * producing an {@code EditProposal}. It therefore fails explicitly rather
 * than claiming a repair was applied and verified.</p>
 *
 * <p>A functional implementation must be composed with a proposal-producing
 * strategy and evidence-backed mutation and verification services.</p>
 */
@ApplicationScoped
public class DefaultClosedLoopCoder implements ClosedLoopCoder {

    @Override
    public Uni<RepairResult> executeLoop(
        ContextPlan plan,
        int maxAttempts,
        Path workspaceRoot,
        String projectId
    ) {
        return Uni.createFrom().item(RepairResult.failed(
                0,
                List.of(),
                "Repair was not executed: no edit proposal or repair strategy is available. " +
                "Refusing to report success without mutation and verification."));
    }
}
