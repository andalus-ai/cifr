package tech.kayys.aljabr.engine.agent;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.andalus.spi.agent.AgentCapabilities;
import tech.kayys.andalus.spi.agent.AgentContract;
import tech.kayys.andalus.spi.agent.AgentContractVersion;
import tech.kayys.andalus.spi.agent.AgentDataContract;
import tech.kayys.andalus.spi.agent.AgentIdentity;
import tech.kayys.andalus.spi.agent.AgentLifecycleFeature;
import tech.kayys.andalus.spi.agent.AgentMetadata;
import tech.kayys.andalus.spi.agent.AgentProtocol;
import tech.kayys.andalus.spi.agent.DefaultAgentContract;

import java.util.Map;
import java.util.Set;

/**
 * Andalus Agent Contract implementation for Aljabr Autonomous Coder.
 *
 * <p>Aljabr serves as the reference autonomous coding agent application
 * built on the Andalus Framework (as documented in Families/Andalus/Doc/andalus-safari/andalus00.md).</p>
 */
@ApplicationScoped
public class AljabrAgentContract implements AgentContract {

    public static final String AGENT_ID = "aljabr";
    public static final String AGENT_NAME = "Aljabr Autonomous Coder";
    public static final String NAMESPACE = "aljabr";

    private final AgentContract delegate;

    public AljabrAgentContract() {
        this.delegate = new DefaultAgentContract(
                AgentIdentity.of(NAMESPACE, AGENT_ID, AGENT_NAME),
                AgentContractVersion.of(1, 0, 0),
                new AgentCapabilities(
                        Set.of("coding", "filesystem", "shell", "git", "testing", "debugging"),
                        Set.of("browser"),
                        Set.of("testing", "debugging")
                ),
                AgentDataContract.any(),
                AgentDataContract.any(),
                Set.of(
                        AgentLifecycleFeature.INITIALIZE,
                        AgentLifecycleFeature.EXECUTE,
                        AgentLifecycleFeature.PAUSE,
                        AgentLifecycleFeature.RESUME,
                        AgentLifecycleFeature.CANCEL,
                        AgentLifecycleFeature.CHECKPOINT,
                        AgentLifecycleFeature.RECOVER,
                        AgentLifecycleFeature.STREAM
                ),
                Set.of(
                        AgentProtocol.IN_PROCESS,
                        AgentProtocol.GRPC,
                        AgentProtocol.HTTP,
                        AgentProtocol.A2A,
                        AgentProtocol.MCP
                ),
                AgentMetadata.of(
                        "Aljabr reference autonomous coding agent application for the Andalus ecosystem.",
                        Map.of(
                                "profile", "coding",
                                "reference_application", "true",
                                "framework", "andalus"
                        )
                )
        );
    }

    @Override
    public AgentIdentity identity() {
        return delegate.identity();
    }

    @Override
    public AgentContractVersion version() {
        return delegate.version();
    }

    @Override
    public AgentCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public AgentDataContract input() {
        return delegate.input();
    }

    @Override
    public AgentDataContract output() {
        return delegate.output();
    }

    @Override
    public Set<AgentLifecycleFeature> lifecycle() {
        return delegate.lifecycle();
    }

    @Override
    public Set<AgentProtocol> protocols() {
        return delegate.protocols();
    }

    @Override
    public AgentMetadata metadata() {
        return delegate.metadata();
    }
}
