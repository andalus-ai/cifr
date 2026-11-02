package tech.kayys.cifr.engine.execution;

import java.util.Map;

/**
 * Sealed algebraic action decision proposed by reasoning cycle.
 */
public sealed interface ActionDecision permits
    ActionDecision.ToolCall,
    ActionDecision.FinalResponse,
    ActionDecision.NeedInformation,
    ActionDecision.Replan {

    record ToolCall(
        String toolName,
        Map<String, Object> arguments,
        String justification
    ) implements ActionDecision {
        public ToolCall {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }
    }

    record FinalResponse(
        String responseContent
    ) implements ActionDecision {}

    record NeedInformation(
        String question
    ) implements ActionDecision {}

    record Replan(
        ReplanReason reason,
        String explanation
    ) implements ActionDecision {}
}
