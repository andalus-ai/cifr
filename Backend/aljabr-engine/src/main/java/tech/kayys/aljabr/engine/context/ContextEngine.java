package tech.kayys.aljabr.engine.context;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

public interface ContextEngine {

    Uni<ContextPacket> assemble(ContextPlan plan, Path workspaceRoot, String workspaceId);
}
