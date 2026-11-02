package tech.kayys.cifr.engine.task.checkpoint;

public record CheckpointId(String value) {
    public static CheckpointId generate() {
        return new CheckpointId("cp-" + java.util.UUID.randomUUID());
    }
}
