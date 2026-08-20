package tech.kayys.aljabr.engine.index;

/**
 * Query object for file index lookups.
 */
public record FileQuery(
    String pathPattern,
    String language,
    String moduleScope,
    boolean testFilesOnly,
    int limit
) {
    public static FileQuery byPattern(String pattern) {
        return new FileQuery(pattern, null, null, false, 50);
    }

    public static FileQuery testsOnly() {
        return new FileQuery(null, null, null, true, 100);
    }

    public static FileQuery inModule(String module) {
        return new FileQuery(null, null, module, false, 100);
    }
}
