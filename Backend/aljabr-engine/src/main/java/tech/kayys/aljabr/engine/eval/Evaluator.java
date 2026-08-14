package tech.kayys.aljabr.engine.eval;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.loop.CodingState;

public interface Evaluator {
    Uni<EvaluationResult> evaluate(CodingState state);
}
