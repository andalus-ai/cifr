package tech.kayys.cifr.engine.repo;

public record ChangeRecord(
    String path,
    ChangeType type,
    String beforeHash,
    String afterHash,
    String patchId,
    String stepId
) {}
