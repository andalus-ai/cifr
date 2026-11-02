package tech.kayys.cifr.engine.architecture;

public record ArchitectureViolation(
    String fromNodeId,
    String toNodeId,
    ArchitectureRule violatedRule,
    String description
) {}
