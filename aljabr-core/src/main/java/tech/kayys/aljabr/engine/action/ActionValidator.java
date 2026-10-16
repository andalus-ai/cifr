package tech.kayys.aljabr.engine.action;

import tech.kayys.aljabr.engine.capability.Capability;
import tech.kayys.aljabr.engine.runtime.state.RuntimeState;

/**
 * Validates action proposal arguments, targets, and preconditions before authorization.
 */
public interface ActionValidator {

    ValidationResult validate(ActionProposal proposal, Capability capability, RuntimeState state);
}
