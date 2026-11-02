package tech.kayys.cifr.engine.action;

/**
 * Target resource descriptor for an action proposal.
 */
public record ActionTarget(
    TargetType type,
    String identifier
) {
    public static ActionTarget file(String path) {
        return new ActionTarget(TargetType.FILE, path);
    }

    public static ActionTarget command(String cmd) {
        return new ActionTarget(TargetType.COMMAND, cmd);
    }
}
