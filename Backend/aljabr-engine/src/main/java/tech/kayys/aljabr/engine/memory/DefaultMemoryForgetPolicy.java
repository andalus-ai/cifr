package tech.kayys.aljabr.engine.memory;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.store.Memory;
import tech.kayys.aljabr.engine.store.MemoryScope;

/**
 * Default implementation of {@link MemoryForgetPolicy}.
 */
@ApplicationScoped
public class DefaultMemoryForgetPolicy implements MemoryForgetPolicy {

    @Override
    public ForgetDecision evaluate(Memory memory, String reason) {
        if (memory == null) {
            return ForgetDecision.REJECT;
        }

        if (reason != null && reason.toLowerCase().contains("user")) {
            return ForgetDecision.FORGET;
        }

        if (memory.scope() == MemoryScope.GLOBAL) {
            return ForgetDecision.ARCHIVE;
        }

        return ForgetDecision.REVOKE;
    }
}
