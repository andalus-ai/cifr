package tech.kayys.aljabr.engine.loop;

import tech.kayys.aljabr.engine.contract.GoalContract;
import tech.kayys.aljabr.engine.runtime.state.RuntimeState;

/**
 * Evaluates whether a GoalContract is satisfied by the observed runtime state.
 */
public interface CompletionEvaluator {

    CompletionResult evaluate(GoalContract contract, RuntimeState state);
}
