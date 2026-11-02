package tech.kayys.cifr.engine.capability;

import io.smallrye.mutiny.Uni;

/**
 * Deterministic authorization gate executing outside the LLM prompt.
 */
public interface CapabilityAuthorizer {

    /**
     * Evaluates whether the requested capability execution is authorized under the current context.
     */
    Uni<AuthorizationResult> authorize(CapabilityRequest request, ToolExecutionContext context);
}
