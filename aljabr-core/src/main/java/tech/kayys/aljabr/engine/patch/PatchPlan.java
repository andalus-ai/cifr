package tech.kayys.aljabr.engine.patch;

import tech.kayys.aljabr.engine.patch.mutation.FileEdit;

import java.util.List;
import java.util.Set;

/**
 * Compiled plan for applying a patch, resolving symbol/line targets to concrete file mutations.
 */
public record PatchPlan(
    List<FileEdit> mutations,
    Set<String> affectedSymbols
) {
    public PatchPlan {
        mutations = mutations != null ? List.copyOf(mutations) : List.of();
        affectedSymbols = affectedSymbols != null ? Set.copyOf(affectedSymbols) : Set.of();
    }
}
