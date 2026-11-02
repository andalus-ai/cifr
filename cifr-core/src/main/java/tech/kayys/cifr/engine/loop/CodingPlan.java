package tech.kayys.cifr.engine.loop;

import java.util.List;

public record CodingPlan(
    String id,
    String description,
    List<String> steps
) {}
