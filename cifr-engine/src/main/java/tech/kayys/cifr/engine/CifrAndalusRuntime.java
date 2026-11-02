package tech.kayys.cifr.engine;

import java.util.concurrent.CompletableFuture;
import io.smallrye.mutiny.Multi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import tech.kayys.cifr.orchestration.CoderOrchestrator;
import tech.kayys.andalus.agent.AgentRequest;
import tech.kayys.andalus.agent.AgentResponse;
import tech.kayys.andalus.core.AgentDefinition;
import tech.kayys.andalus.core.runtime.AndalusRuntime;
import tech.kayys.andalus.extension.Id;
import tech.kayys.andalus.extension.Metadata;
import tech.kayys.andalus.identity.ResourceId;
import tech.kayys.andalus.resource.ResourceType;

@ApplicationScoped
public class CifrAndalusRuntime implements AndalusRuntime {

    private final String id;
    private final Metadata metadata;
    private final ResourceType type = new ResourceType.Custom("runtime");

    @Inject
    CoderOrchestrator coderOrchestrator;

    public CifrAndalusRuntime() {
        this.id = Id.random().asString();
        this.metadata = Metadata.builder()
            .name("cifr-andalus-runtime")
            .description("Cifr Specialized Coding Runtime")
            .version("0.1.0")
            .label("type", "runtime")
            .now()
            .build();
    }

    @Override
    public CompletableFuture<AgentResponse> executeAsync(AgentDefinition agent, AgentRequest request) {
        // We delegate the execution to Cifr's specialized CoderOrchestrator.
        return coderOrchestrator.execute(request).subscribeAsCompletionStage();
    }


    @Override
    public boolean supports(AgentDefinition agent) {
        if (agent == null || agent.metadata() == null || agent.metadata().labels() == null) {
            return false;
        }
        
        // This runtime ONLY supports agents explicitly labeled as 'coder'
        String agentType = agent.metadata().labels().get("type");
        return "coder".equals(agentType);
    }

    @Override
    public tech.kayys.andalus.identity.ResourceId id() {
        return tech.kayys.andalus.identity.ResourceId.from(Id.fromString(id), type);
    }

    @Override
    public ResourceType type() {
        return type;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }
}
