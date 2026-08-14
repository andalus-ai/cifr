package tech.kayys.aljabr.engine.eval;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.loop.CodingState;

@ApplicationScoped
public class DefaultEvaluator implements Evaluator {

    @Override
    public Uni<EvaluationResult> evaluate(CodingState state) {
        // MVP: Assume pass
        return Uni.createFrom().item(new EvaluationResult(1.0, EvaluationDecision.PASS, "MVP basic evaluation passed."));
    }
}
