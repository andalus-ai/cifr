package tech.kayys.aljabr.engine.plan;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface GoalDecomposer {

    Uni<List<PlanStep>> decompose(String goal);
}
