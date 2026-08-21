package tech.kayys.aljabr.engine.memory;

import tech.kayys.aljabr.engine.store.Memory;

/**
 * Policy governing intentional forgetting, revocation, and archival of memories upon user request or contradiction.
 */
public interface MemoryForgetPolicy {

    ForgetDecision evaluate(Memory memory, String reason);
}
