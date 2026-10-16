package tech.kayys.aljabr.engine.retrieval;

/**
 * Origin source classification for retrieved context candidates.
 */
public enum CandidateSource {
    MEMORY,
    EVIDENCE,
    TASK_STATE,
    FILE,
    TOOL_RESULT,
    WEB_RESULT
}
