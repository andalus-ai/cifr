package tech.kayys.aljabr.engine.verification.graph;

import java.util.List;

public interface CompletionPolicy {

    CompletionDecision evaluate(VerificationGraph graph, List<Requirement> requirements);
}
