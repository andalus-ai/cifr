package tech.kayys.cifr.engine.exec.intelligence;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

public interface ExecutionIntelligence {

    Uni<ExecutionOutcome> execute(String executionId, ProcessSpec spec, ProjectToolchain toolchain);
}
