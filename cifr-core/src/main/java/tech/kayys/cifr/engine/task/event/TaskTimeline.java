package tech.kayys.cifr.engine.task.event;

import tech.kayys.cifr.engine.task.TaskId;

import java.util.List;
import java.util.stream.Collectors;

public record TaskTimeline(TaskId taskId, List<TaskEvent> events) {
    public TaskTimeline {
        events = events != null ? List.copyOf(events) : List.of();
    }

    public String render() {
        return events.stream()
            .map(e -> e.occurredAt() + "  " + e.type() + " " + e.payload())
            .collect(Collectors.joining("\n"));
    }
}
