package tech.kayys.cifr.engine.capability;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Default deterministic capability authorizer.
 * Enforces risk policies outside the LLM prompt.
 */
@ApplicationScoped
public class DefaultCapabilityAuthorizer implements CapabilityAuthorizer {

    private static final Logger LOG = Logger.getLogger(DefaultCapabilityAuthorizer.class);

    @Inject
    public CapabilityRegistry capabilityRegistry;

    public DefaultCapabilityAuthorizer() {}

    public DefaultCapabilityAuthorizer(CapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
    }

    @Override
    public Uni<AuthorizationResult> authorize(CapabilityRequest request, ToolExecutionContext context) {
        if (request == null) {
            return Uni.createFrom().item(AuthorizationResult.deny("Null capability request"));
        }

        if (context != null && context.cancellationToken() != null && context.cancellationToken().isCancelled()) {
            return Uni.createFrom().item(
                AuthorizationResult.deny("Execution cancelled: " + context.cancellationToken().cancellationReason().orElse("Aborted"))
            );
        }

        // System and internal invocations are trusted
        if (request.origin() == RequestOrigin.SYSTEM || request.origin() == RequestOrigin.INTERNAL) {
            return Uni.createFrom().item(AuthorizationResult.allow());
        }

        Optional<Capability> capOpt = capabilityRegistry != null ? capabilityRegistry.find(request.capabilityId()) : Optional.empty();
        if (capOpt.isEmpty()) {
            return Uni.createFrom().item(AuthorizationResult.deny("Unregistered capability: " + request.capabilityId()));
        }

        Capability cap = capOpt.get();
        CapabilityRisk risk = cap.risk();

        if (risk == CapabilityRisk.CRITICAL) {
            LOG.warnf("Capability [%s] marked CRITICAL requires explicit user confirmation.", cap.id());
            return Uni.createFrom().item(
                AuthorizationResult.allowWithConfirmation("High-risk destructive capability requires user confirmation")
            );
        }

        if (risk == CapabilityRisk.HIGH && request.origin() == RequestOrigin.MODEL) {
            LOG.infof("Capability [%s] marked HIGH requires confirmation for autonomous model calls.", cap.id());
            return Uni.createFrom().item(
                AuthorizationResult.allowWithConfirmation("External or elevated risk capability requires confirmation")
            );
        }

        return Uni.createFrom().item(AuthorizationResult.allow());
    }
}
