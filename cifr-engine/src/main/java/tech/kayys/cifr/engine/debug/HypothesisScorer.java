package tech.kayys.cifr.engine.debug;

public interface HypothesisScorer {

    HypothesisScore score(Hypothesis hypothesis, DebugContext context);
}
