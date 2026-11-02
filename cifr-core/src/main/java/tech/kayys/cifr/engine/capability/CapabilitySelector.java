package tech.kayys.cifr.engine.capability;

import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * Preselects relevant capabilities based on the active task description and loaded skills.
 */
public interface CapabilitySelector {

    /**
     * Returns the subset of registered capabilities relevant for the task and active skills.
     */
    Uni<List<Capability>> select(String taskDescription, List<String> activeSkillIds, CapabilityLevel level);
}
