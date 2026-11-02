package tech.kayys.cifr.engine.tool;

import java.util.List;
import tech.kayys.cifr.engine.loop.CodingTask;
import tech.kayys.cifr.engine.plan.PlanStep;

public interface ToolSelector {
    List<ToolDescriptor> select(CodingTask task, PlanStep step);
}
