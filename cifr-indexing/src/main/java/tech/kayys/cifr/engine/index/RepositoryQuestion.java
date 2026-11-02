package tech.kayys.cifr.engine.index;

/**
 * A structured question that an agent poses to the Repository Intelligence layer.
 * Strongly typed to reduce model decision space and enable deterministic routing.
 */
public record RepositoryQuestion(
    QuestionType type,
    String target,
    RepositoryScope scope,
    int maxResults
) {
    public static RepositoryQuestion whatIs(String target) {
        return new RepositoryQuestion(QuestionType.WHAT_IS_THIS, target, RepositoryScope.all(), 5);
    }

    public static RepositoryQuestion whoCalls(String symbolId) {
        return new RepositoryQuestion(QuestionType.WHO_CALLS, symbolId, RepositoryScope.all(), 20);
    }

    public static RepositoryQuestion testsFor(String target) {
        return new RepositoryQuestion(QuestionType.WHAT_TESTS_COVER_THIS, target, RepositoryScope.all(), 20);
    }

    public static RepositoryQuestion impactOf(String changeTarget) {
        return new RepositoryQuestion(QuestionType.WHAT_WILL_CHANGE_AFFECT, changeTarget, RepositoryScope.all(), 50);
    }
}
