package tech.kayys.aljabr.engine.skill;

import java.util.Set;

public record SkillPhasePolicy(Set<String> supportedPhases, String entryPhase) {
    public SkillPhasePolicy {
        supportedPhases = supportedPhases != null ? Set.copyOf(supportedPhases) : Set.of();
    }

    public boolean supports(String phase) {
        return supportedPhases.contains(phase);
    }
}
