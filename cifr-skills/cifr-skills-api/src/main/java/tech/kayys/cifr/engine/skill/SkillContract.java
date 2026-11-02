package tech.kayys.cifr.engine.skill;

import java.util.List;

public record SkillContract(
    SkillDescriptor descriptor,
    List<SkillPrecondition> preconditions,
    List<SkillPostcondition> postconditions
) {
    public SkillContract {
        preconditions = preconditions != null ? List.copyOf(preconditions) : List.of();
        postconditions = postconditions != null ? List.copyOf(postconditions) : List.of();
    }
}
