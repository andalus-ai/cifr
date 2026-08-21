package tech.kayys.aljabr.engine.loop;

import tech.kayys.aljabr.engine.action.ActionProposal;
import tech.kayys.aljabr.engine.retrieval.RetrievalQuery;

import java.util.Optional;

/**
 * Structured decision emitted by the model during a single decision cycle.
 */
public record AgentDecision(
    LoopDecisionType type,
    Optional<ActionProposal> action,
    Optional<RetrievalQuery> contextRequest,
    Optional<UserQuestion> userQuestion,
    Optional<String> response,
    Optional<CompletionClaim> completion
) {
    public static AgentDecision act(ActionProposal action) {
        return new AgentDecision(LoopDecisionType.ACT, Optional.of(action), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static AgentDecision retrieve(RetrievalQuery query) {
        return new AgentDecision(LoopDecisionType.RETRIEVE, Optional.empty(), Optional.of(query), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static AgentDecision respond(String response) {
        return new AgentDecision(LoopDecisionType.RESPOND, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(response), Optional.empty());
    }

    public static AgentDecision askUser(UserQuestion question) {
        return new AgentDecision(LoopDecisionType.ASK_USER, Optional.empty(), Optional.empty(), Optional.of(question), Optional.empty(), Optional.empty());
    }

    public static AgentDecision complete(CompletionClaim claim) {
        return new AgentDecision(LoopDecisionType.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(claim));
    }
}
