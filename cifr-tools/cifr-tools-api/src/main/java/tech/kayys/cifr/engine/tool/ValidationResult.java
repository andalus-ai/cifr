package tech.kayys.cifr.engine.tool;

import java.util.Optional;

public record ValidationResult(
    boolean isValid,
    Optional<String> reason
) {}
