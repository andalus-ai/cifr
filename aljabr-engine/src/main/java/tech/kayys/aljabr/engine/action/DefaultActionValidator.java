package tech.kayys.aljabr.engine.action;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.capability.Capability;
import tech.kayys.aljabr.engine.runtime.state.RuntimeState;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link ActionValidator}.
 */
@ApplicationScoped
public class DefaultActionValidator implements ActionValidator {

    @Override
    public ValidationResult validate(ActionProposal proposal, Capability capability, RuntimeState state) {
        if (proposal == null) {
            return ValidationResult.failure(List.of("Action proposal is null"));
        }

        List<String> errors = new ArrayList<>();

        if (proposal.id() == null || proposal.id().isBlank()) {
            errors.add("Proposal ID is missing");
        }

        if (proposal.target() == null) {
            errors.add("Action target is missing");
        }

        if (capability != null && capability.contract() != null) {
            // Check preconditions if any
            for (var pre : capability.contract().preconditions()) {
                if (pre.isMandatory() && pre.conditionExpression().contains("workspace") && state == null) {
                    errors.add("Precondition failed: missing runtime state for " + pre.id());
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}
