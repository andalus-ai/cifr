package tech.kayys.aljabr.engine;

import java.util.concurrent.CompletableFuture;
import io.smallrye.mutiny.Multi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import tech.kayys.aljabr.orchestration.CoderOrchestrator;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.core.AgentDefinition;
import tech.kayys.wayang.core.runtime.WayangRuntime;
import tech.kayys.wayang.extension.Id;
import tech.kayys.wayang.extension.Metadata;
import tech.kayys.wayang.identity.ResourceId;
import tech.kayys.wayang.resource.ResourceType;

@ApplicationScoped
public class AljabrWayangRuntime implements WayangRuntime {

    private final String id;
    private final Metadata metadata;
    private final ResourceType type = new ResourceType.Custom("runtime");

    @Inject
    CoderOrchestrator coderOrchestrator;

    public AljabrWayangRuntime() {
        this.id = Id.random().asString();
        this.metadata = Metadata.builder()
            .name("aljabr-wayang-runtime")
            .description("Aljabr Specialized Coding Runtime")
            .version("0.1.0")
            .label("type", "runtime")
            .now()
            .build();
    }

    @Override
    public CompletableFuture<AgentResponse> executeAsync(AgentDefinition agent, AgentRequest request) {
        // We delegate the execution to Aljabr's specialized CoderOrchestrator.
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
    public tech.kayys.wayang.identity.ResourceId id() {
        return tech.kayys.wayang.identity.ResourceId.from(Id.fromString(id), type);
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
