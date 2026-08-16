package tech.kayys.aljabr.engine.orchestrator.toolchain;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

public interface ProjectDetector {

    Uni<ProjectToolchain> detect(Path workspaceRoot);
}
