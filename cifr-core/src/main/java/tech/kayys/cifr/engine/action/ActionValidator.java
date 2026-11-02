package tech.kayys.cifr.engine.action;

import tech.kayys.cifr.engine.capability.Capability;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;

/**
 * Validates action proposal arguments, targets, and preconditions before authorization.
 */
public interface ActionValidator {

    ValidationResult validate(ActionProposal proposal, Capability capability, RuntimeState state);
}
