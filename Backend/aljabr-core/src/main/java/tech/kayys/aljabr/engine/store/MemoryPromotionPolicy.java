package tech.kayys.aljabr.engine.store;

import tech.kayys.aljabr.engine.lifecycle.AgentStateContext;

/**
 * Policy governing when an observed piece of evidence qualifies for promotion to durable memory.
 */
public interface MemoryPromotionPolicy {

    PromotionDecision evaluate(Evidence evidence, AgentStateContext context);
}
