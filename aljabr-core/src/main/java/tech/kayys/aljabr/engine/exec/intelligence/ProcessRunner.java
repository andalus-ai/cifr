package tech.kayys.aljabr.engine.exec.intelligence;

import io.smallrye.mutiny.Uni;

public interface ProcessRunner {

    Uni<ProcessResult> execute(ProcessSpec spec);

    Uni<Void> cancel(String processId);
}
