package tech.kayys.aljabr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;

public interface OutputProcessor {

    Uni<ProcessedOutput> process(int exitCode, String stdout, String stderr, long maxOutputBytes);
}
