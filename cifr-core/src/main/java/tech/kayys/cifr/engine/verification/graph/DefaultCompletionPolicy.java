package tech.kayys.cifr.engine.verification.graph;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DefaultCompletionPolicy implements CompletionPolicy {

    @Override
    public CompletionDecision evaluate(VerificationGraph graph, List<Requirement> requirements) {
        // Missing policy input is not proof of completion. In particular, an empty
        // requirement set must not turn an unverified task into a successful one.
        if (graph == null || requirements == null || requirements.isEmpty()) {
            return CompletionDecision.NEED_MORE_VERIFICATION;
        }

        Set<String> requirementIds = new HashSet<>();
        for (Requirement requirement : requirements) {
            if (requirement == null || requirement.id() == null || requirement.id().isBlank()
                    || !requirementIds.add(requirement.id())) {
                return CompletionDecision.BLOCKED;
            }
        }

        boolean allVerified = true;
        for (Requirement requirement : requirements) {
            if (requirement.status() == RequirementStatus.FAILED) {
                return CompletionDecision.NEED_REPAIR;
            }

            if (!graph.isRequirementVerified(requirement.id())) {
                allVerified = false;
                String requirementNodeId = "req:" + requirement.id();
                boolean hasContradiction = graph.allEdges().stream().anyMatch(edge ->
                        (edge.to().nodeId().equals(requirementNodeId)
                                || edge.from().nodeId().equals(requirementNodeId))
                                && edge.type() == EdgeType.CONTRADICTS);
                if (hasContradiction) {
                    return CompletionDecision.NEED_REPAIR;
                }
            }
        }

        return allVerified ? CompletionDecision.COMPLETE : CompletionDecision.NEED_MORE_VERIFICATION;
    }
}
