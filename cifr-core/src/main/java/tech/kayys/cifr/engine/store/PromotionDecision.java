package tech.kayys.cifr.engine.store;

/**
 * Decision whether to promote an observation into retained memory.
 */
public enum PromotionDecision {
    PROMOTE,
    IGNORE,
    DEFER
}
