package tech.kayys.cifr.engine.task.snapshot;

public interface StateMigrator {
    boolean supports(int fromVersion, int toVersion);
    StateSnapshot migrate(StateSnapshot snapshot);
}
