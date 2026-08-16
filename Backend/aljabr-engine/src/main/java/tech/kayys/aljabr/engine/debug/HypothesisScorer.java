package tech.kayys.aljabr.engine.debug;

public interface HypothesisScorer {

    HypothesisScore score(Hypothesis hypothesis, DebugContext context);
}
