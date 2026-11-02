package tech.kayys.cifr.engine.index.symbol;

/**
 * Language analyzer abstraction — parses a source file into normalized symbols,
 * references, imports, and test candidates. Implementations should be language-specific
 * (JavaAnalyzer, KotlinAnalyzer, TypeScriptAnalyzer, etc.) and registered as CDI beans.
 */
public interface LanguageAnalyzer {

    /** Returns true if this analyzer handles the given language identifier (e.g. "java", "kotlin"). */
    boolean supports(String language);

    /**
     * Parse the source file at the given path (relative to workspace root).
     *
     * @param fileId   Stable identifier for this file in the index.
     * @param filePath Workspace-relative path.
     * @param source   Raw source content.
     * @return A normalized {@link ParsedFile}.
     */
    ParsedFile parse(String fileId, String filePath, String source);
}
