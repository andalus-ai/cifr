package tech.kayys.cifr.engine.eval;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.loop.CodingState;

public interface Evaluator {
    Uni<EvaluationResult> evaluate(CodingState state);
}
