package tech.kayys.aljabr.engine.exec.intelligence;

import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

public interface OutputNormalizer {

    NormalizedOutput normalize(ProcessResult result, ProjectToolchain toolchain);
}
