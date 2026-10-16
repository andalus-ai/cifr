package tech.kayys.aljabr.engine.verification.graph;

public sealed interface VerificationNode {

    String nodeId();

    record RequirementNode(Requirement requirement) implements VerificationNode {
        @Override
        public String nodeId() {
            return "req:" + requirement.id();
        }
    }

    record CheckNode(VerificationCheckId checkId) implements VerificationNode {
        @Override
        public String nodeId() {
            return "chk:" + checkId.value();
        }
    }

    record EvidenceNode(VerificationEvidence evidence) implements VerificationNode {
        @Override
        public String nodeId() {
            return "ev:" + evidence.evidenceId();
        }
    }

    record ExecutionNode(String executionId) implements VerificationNode {
        @Override
        public String nodeId() {
            return "exec:" + executionId;
        }
    }
}
