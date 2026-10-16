package tech.kayys.aljabr.engine.patch.transaction;

public record ChangeBudget(
    int maxFiles,
    int maxChangedLines,
    int maxNewDependencies,
    boolean allowPublicApiChange,
    boolean allowConfigChange
) {
    public static ChangeBudget defaultBudget() {
        return new ChangeBudget(5, 100, 0, false, false);
    }

    public static ChangeBudget targeted() {
        return new ChangeBudget(2, 30, 0, false, false);
    }

    public static ChangeBudget permissive() {
        return new ChangeBudget(20, 500, 3, true, true);
    }
}
