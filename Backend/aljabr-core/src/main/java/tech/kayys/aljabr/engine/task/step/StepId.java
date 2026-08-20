package tech.kayys.aljabr.engine.task.step;

public record StepId(String value) {
    public static StepId generate() {
        return new StepId("step-" + java.util.UUID.randomUUID());
    }
}
