package tech.kayys.cifr.engine.orchestrator.policy;

import tech.kayys.cifr.engine.orchestrator.ToolIntent;
import tech.kayys.cifr.engine.tool.protocol.ToolCapability;

/**
 * Policy gate that evaluates a ToolRequest before execution.
 * Returns a ToolDecision that controls whether, how, or where the tool executes.
 */
public interface ToolExecutionPolicy {

    ToolDecision evaluate(
        ToolIntent intent,
        ToolCapability capability,
        ExecutionEnvironment environment
    );

    /**
     * Optionally provide a redirect target when decision is REDIRECT.
     */
    default ToolIntent redirect(ToolIntent intent, ExecutionEnvironment environment) {
        return intent;
    }
}
