package tech.kayys.cifr.engine.process;

import io.smallrye.mutiny.Uni;
import java.io.InputStream;

public interface OutputCollector {

    Uni<OutputArtifact> collect(
        ProcessId processId,
        InputStream stdout,
        InputStream stderr,
        OutputPolicy policy
    );
}
