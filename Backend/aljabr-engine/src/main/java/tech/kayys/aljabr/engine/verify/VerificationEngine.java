package tech.kayys.aljabr.engine.verify;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.loop.CodingTask;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;

import java.util.List;

public interface VerificationEngine {
    Uni<VerificationReport> verify(CodingTask task, ChangeSet changes, AgentToolExecutor executor, List<Tool> tools);
}
