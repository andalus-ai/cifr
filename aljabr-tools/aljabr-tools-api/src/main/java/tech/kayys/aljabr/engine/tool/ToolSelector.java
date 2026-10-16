package tech.kayys.aljabr.engine.tool;

import java.util.List;
import tech.kayys.aljabr.engine.loop.CodingTask;
import tech.kayys.aljabr.engine.plan.PlanStep;

public interface ToolSelector {
    List<ToolDescriptor> select(CodingTask task, PlanStep step);
}
