package tech.kayys.cifr.engine.patch;

import java.util.List;

public record VerificationAction(
    String type,
    String target,
    List<String> arguments
) {
    public VerificationAction {
        arguments = arguments != null ? List.copyOf(arguments) : List.of();
    }
}
