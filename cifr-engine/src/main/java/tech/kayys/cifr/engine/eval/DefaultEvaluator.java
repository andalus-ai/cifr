package tech.kayys.cifr.engine.eval;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.loop.CodingState;

@ApplicationScoped
public class DefaultEvaluator implements Evaluator {
    @Override
    public Uni<EvaluationResult> evaluate(CodingState state) {
        return Uni.createFrom().item(new EvaluationResult(1.0, EvaluationDecision.PASS, "Evaluation passed"));
    }
}
