package tech.kayys.cifr.engine.verification.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultCompletionPolicyTest {

    private final DefaultCompletionPolicy policy = new DefaultCompletionPolicy();

    @Test
    void missingGraphDoesNotImplyCompletion() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(null, List.of(Requirement.of(
                        "compile", "Compile the project", RequirementType.STRUCTURAL, RequirementPriority.MANDATORY))));
    }

    @Test
    void emptyRequirementsDoNotImplyCompletion() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(new VerificationGraph(), List.of()));
    }

    @Test
    void unverifiedRequirementRequiresMoreVerification() {
        Requirement requirement = Requirement.of(
                "tests", "Run relevant tests", RequirementType.BEHAVIORAL, RequirementPriority.MANDATORY);

        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(new VerificationGraph(), List.of(requirement)));
    }

    @Test
    void supportedRequirementCanComplete() {
        Requirement requirement = Requirement.of(
                "compile", "Compile the project", RequirementType.STRUCTURAL, RequirementPriority.MANDATORY);
        VerificationGraph graph = new VerificationGraph();
        VerificationNode.RequirementNode requirementNode =
                new VerificationNode.RequirementNode(requirement);
        VerificationNode.CheckNode checkNode =
                new VerificationNode.CheckNode(VerificationCheckId.of("build.compile"));
        graph.addEdge(checkNode, requirementNode, EdgeType.VERIFIED_BY);

        assertEquals(CompletionDecision.COMPLETE, policy.evaluate(graph, List.of(requirement)));
    }

    @Test
    void contradictoryRequirementNeedsRepair() {
        Requirement requirement = Requirement.of(
                "tests", "Tests pass", RequirementType.BEHAVIORAL, RequirementPriority.MANDATORY);
        VerificationGraph graph = new VerificationGraph();
        VerificationNode.RequirementNode requirementNode =
                new VerificationNode.RequirementNode(requirement);
        VerificationNode.CheckNode checkNode =
                new VerificationNode.CheckNode(VerificationCheckId.of("tests.junit"));
        graph.addEdge(checkNode, requirementNode, EdgeType.CONTRADICTS);

        assertEquals(CompletionDecision.NEED_REPAIR, policy.evaluate(graph, List.of(requirement)));
    }

    @Test
    void failedRequirementNeedsRepair() {
        Requirement requirement = new Requirement(
                "tests", "Tests pass", RequirementType.BEHAVIORAL,
                RequirementPriority.MANDATORY, RequirementStatus.FAILED);

        assertEquals(CompletionDecision.NEED_REPAIR,
                policy.evaluate(new VerificationGraph(), List.of(requirement)));
    }

    @Test
    void duplicateRequirementIdsBlockEvaluation() {
        Requirement first = Requirement.of(
                "tests", "Tests pass", RequirementType.BEHAVIORAL, RequirementPriority.MANDATORY);
        Requirement duplicate = Requirement.of(
                "tests", "Run tests", RequirementType.STRUCTURAL, RequirementPriority.MANDATORY);

        assertEquals(CompletionDecision.BLOCKED,
                policy.evaluate(new VerificationGraph(), List.of(first, duplicate)));
    }
}
