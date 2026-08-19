package tech.kayys.aljabr.engine.skill.review;

import java.nio.file.Path;

public record ReviewFinding(
    Path file,
    int line,
    String issue,
    String suggestion,
    String severity
) {}
