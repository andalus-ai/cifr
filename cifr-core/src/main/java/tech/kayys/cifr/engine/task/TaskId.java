package tech.kayys.cifr.engine.task;

public record TaskId(String value) {
    public static TaskId of(String value) { return new TaskId(value); }
    public static TaskId generate() { return new TaskId("task-" + java.util.UUID.randomUUID()); }
}
