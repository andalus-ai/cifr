package tech.kayys.aljabr.engine.index;

/**
 * Scope that constrains a repository question (module, package, or wildcard).
 */
public record RepositoryScope(
    String moduleId,
    String packagePrefix,
    boolean includeTests
) {
    public static RepositoryScope all() {
        return new RepositoryScope(null, null, true);
    }

    public static RepositoryScope module(String moduleId) {
        return new RepositoryScope(moduleId, null, false);
    }

    public static RepositoryScope pkg(String packagePrefix) {
        return new RepositoryScope(null, packagePrefix, false);
    }
}
