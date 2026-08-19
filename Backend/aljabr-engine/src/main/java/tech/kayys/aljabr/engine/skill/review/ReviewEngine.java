package tech.kayys.aljabr.engine.skill.review;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.exec.ExecutionEvidence;
import tech.kayys.aljabr.engine.patch.ChangePlan;

public interface ReviewEngine {

    Uni<ReviewResult> review(ChangePlan plan, ExecutionEvidence evidence);
}
