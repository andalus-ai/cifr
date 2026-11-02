package tech.kayys.cifr.engine.loop;

import tech.kayys.cifr.engine.contract.GoalContract;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;

/**
 * Evaluates whether a GoalContract is satisfied by the observed runtime state.
 */
public interface CompletionEvaluator {

    CompletionResult evaluate(GoalContract contract, RuntimeState state);
}
