package tech.kayys.aljabr.engine.tool;

import java.util.Optional;

public record ValidationResult(
    boolean isValid,
    Optional<String> reason
) {}
