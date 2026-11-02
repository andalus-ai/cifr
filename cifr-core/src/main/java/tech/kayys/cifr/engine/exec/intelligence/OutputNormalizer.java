package tech.kayys.cifr.engine.exec.intelligence;

import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

public interface OutputNormalizer {

    NormalizedOutput normalize(ProcessResult result, ProjectToolchain toolchain);
}
