package tech.kayys.aljabr.engine.patch;

import tech.kayys.aljabr.engine.repo.RepositorySnapshot;

/**
 * Compiles a high-level patch proposal into a concrete, executable PatchPlan
 * using repository snapshot intelligence (symbol resolution, import detection).
 */
public interface PatchCompiler {

    PatchPlan compile(PatchProposal proposal, RepositorySnapshot snapshot);
}
