package tech.kayys.cifr.engine.verification.graph;

public record Requirement(
    String id,
    String description,
    RequirementType type,
    RequirementPriority priority,
    RequirementStatus status
) {
    public static Requirement of(String id, String description, RequirementType type, RequirementPriority priority) {
        return new Requirement(id, description, type, priority, RequirementStatus.UNVERIFIED);
    }
}
