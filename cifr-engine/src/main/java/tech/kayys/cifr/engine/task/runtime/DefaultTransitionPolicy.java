package tech.kayys.cifr.engine.task.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.task.TaskPhase;
import tech.kayys.cifr.engine.task.TaskState;

@ApplicationScoped
public class DefaultTransitionPolicy implements TransitionPolicy {
    @Override
    public TransitionDecision evaluate(TaskState current, AgentSignal signal) {
        if (current == null || current.phase() == null) {
            return TransitionDecision.to(TaskPhase.CREATED, "Initialize");
        }

        return switch (current.phase()) {
            case CREATED -> TransitionDecision.to(TaskPhase.UNDERSTANDING, "Starting task");
            case UNDERSTANDING -> TransitionDecision.to(TaskPhase.INVESTIGATING, "Goal understood");
            case INVESTIGATING -> {
                if (signal instanceof AgentSignal.PhaseDone) {
                    yield TransitionDecision.to(TaskPhase.PLANNING, "Investigation complete");
                }
                yield TransitionDecision.to(TaskPhase.INVESTIGATING, "Need more context");
            }
            case PLANNING -> TransitionDecision.to(TaskPhase.PATCHING, "Plan created");
            case PATCHING -> {
                if (signal instanceof AgentSignal.PatchApplied) {
                    yield TransitionDecision.to(TaskPhase.VALIDATING, "Patch applied");
                }
                if (signal instanceof AgentSignal.StepFailed) {
                    yield TransitionDecision.to(TaskPhase.INVESTIGATING, "Patch conflict");
                }
                yield TransitionDecision.to(TaskPhase.PATCHING, "Patching in progress");
            }
            case VALIDATING -> {
                if (signal instanceof AgentSignal.TestsFailed) {
                    yield TransitionDecision.to(TaskPhase.REPAIRING, "Validation failed");
                }
                yield TransitionDecision.to(TaskPhase.VERIFYING, "Validation passed");
            }
            case VERIFYING -> {
                if (signal instanceof AgentSignal.RequirementsVerified) {
                    yield TransitionDecision.to(TaskPhase.REVIEWING, "All verified");
                }
                if (signal instanceof AgentSignal.TestsFailed) {
                    yield TransitionDecision.to(TaskPhase.REPAIRING, "Verification failed");
                }
                yield TransitionDecision.to(TaskPhase.VERIFYING, "Verifying");
            }
            case REVIEWING -> {
                if (signal instanceof AgentSignal.ReviewAccepted) {
                    yield TransitionDecision.to(TaskPhase.COMPLETED, "Review accepted");
                }
                yield TransitionDecision.to(TaskPhase.REPAIRING, "Review concerns");
            }
            case REPAIRING -> TransitionDecision.to(TaskPhase.PATCHING, "Repair planned");
            default -> TransitionDecision.to(current.phase(), "No transition");
        };
    }
}
