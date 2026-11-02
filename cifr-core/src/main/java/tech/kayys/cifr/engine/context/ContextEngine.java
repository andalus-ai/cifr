package tech.kayys.cifr.engine.context;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

/**
 * Assembles model-facing context packets from a {@link ContextPlan} and the live workspace.
 */
public interface ContextEngine {

    /**
     * Assembles a {@link ContextPacket} suitable for a single model invocation.
     */
    Uni<ContextPacket> assemble(ContextPlan plan, Path workspaceRoot, String projectId);
}
