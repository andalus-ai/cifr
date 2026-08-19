package tech.kayys.aljabr.engine.exec.intelligence;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

public interface ExecutionIntelligence {

    Uni<ExecutionOutcome> execute(String executionId, ProcessSpec spec, ProjectToolchain toolchain);
}
