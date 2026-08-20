package tech.kayys.aljabr.engine.index;

/**
 * Structured question types that an agent can ask about the repository.
 * Using strongly-typed questions (rather than freeform queries) reduces
 * the model's decision space and enables deterministic routing.
 */
public enum QuestionType {
    /** What is this symbol / file / module? */
    WHAT_IS_THIS,
    /** Where is this symbol defined? */
    WHERE_DEFINED,
    /** Who calls / uses this symbol? */
    WHO_CALLS,
    /** What does this symbol/module depend on? */
    WHAT_DOES_THIS_DEPEND_ON,
    /** What depends on this symbol/module? */
    WHAT_DEPENDS_ON_THIS,
    /** Which tests cover this symbol/file? */
    WHAT_TESTS_COVER_THIS,
    /** What will a proposed change affect? */
    WHAT_WILL_CHANGE_AFFECT
}
