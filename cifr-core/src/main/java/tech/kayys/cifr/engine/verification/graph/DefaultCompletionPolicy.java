package tech.kayys.cifr.engine.verification.graph;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class DefaultCompletionPolicy implements CompletionPolicy {

    @Override
    public CompletionDecision evaluate(VerificationGraph graph, List<Requirement> requirements) {
        if (graph == null || requirements == null || requirements.isEmpty()) {
            return CompletionDecision.COMPLETE;
        }

        int verifiedCount = 0;
        int failedCount = 0;

        for (Requirement req : requirements) {
            if (graph.isRequirementVerified(req.id())) {
                verifiedCount++;
            } else {
                // Check if there is explicit contradiction
                String reqNodeId = "req:" + req.id();
                boolean hasContradiction = graph.allEdges().stream().anyMatch(e ->
                    (e.to().nodeId().equals(reqNodeId) || e.from().nodeId().equals(reqNodeId)) &&
                    e.type() == EdgeType.CONTRADICTS
                );
                if (hasContradiction) {
                    failedCount++;
                }
            }
        }

        if (failedCount > 0) {
            return CompletionDecision.NEED_REPAIR;
        }

        if (verifiedCount == requirements.size()) {
            return CompletionDecision.COMPLETE;
        }

        // If some requirements are not yet verified
        return CompletionDecision.NEED_MORE_VERIFICATION;
    }
}
