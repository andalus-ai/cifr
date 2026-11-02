package tech.kayys.cifr.engine.memory;

import tech.kayys.cifr.engine.store.Memory;

/**
 * Policy governing intentional forgetting, revocation, and archival of memories upon user request or contradiction.
 */
public interface MemoryForgetPolicy {

    ForgetDecision evaluate(Memory memory, String reason);
}
