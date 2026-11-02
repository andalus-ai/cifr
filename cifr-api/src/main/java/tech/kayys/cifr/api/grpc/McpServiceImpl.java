package tech.kayys.cifr.api.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.andalus.agent.mcp.McpConfigLoader;
import tech.kayys.andalus.agent.mcp.McpServerConfig;
import tech.kayys.andalus.agent.mcp.McpSkillBridge;
import tech.kayys.andalus.agent.mcp.McpTransportType;
import tech.kayys.andalus.pro.api.grpc.*;

import java.util.List;
import java.util.Map;

@GrpcService
@jakarta.inject.Singleton
public class McpServiceImpl extends MutinyMcpServiceGrpc.McpServiceImplBase implements McpService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    McpConfigLoader configLoader;

    @Inject
    McpSkillBridge skillBridge;

    @Override
    public Uni<ListMcpServersResponse> listServers(Empty request) {
        List<McpServerInfo> list = configLoader.loadMergedConfigs().values().stream()
            .map(this::toServerInfo)
            .toList();

        return Uni.createFrom().item(ListMcpServersResponse.newBuilder()
            .addAllServers(list)
            .build());
    }

    @Override
    public Uni<McpServerInfo> registerServer(RegisterMcpServerRequest request) {
        McpTransportType transport = "stdio".equalsIgnoreCase(request.getTransport())
            ? McpTransportType.STDIO
            : McpTransportType.HTTP;

        McpServerConfig config = new McpServerConfig(
            request.getId(),
            request.getUrl().isBlank() ? null : request.getUrl(),
            request.getCommand().isBlank() ? null : request.getCommand(),
            request.getArgsList(),
            transport,
            request.getEnabled(),
            Map.of()
        );

        return skillBridge.connect(config)
            .map(tools -> toServerInfo(config));
    }

    @Override
    public Uni<RemoveMcpServerResponse> removeServer(RemoveMcpServerRequest request) {
        return skillBridge.disconnect(request.getId())
            .map(v -> RemoveMcpServerResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Server " + request.getId() + " disconnected and removed")
                .build());
    }

    @Override
    public Uni<ReloadMcpResponse> reload(Empty request) {
        return configLoader.reload()
            .map(tools -> ReloadMcpResponse.newBuilder()
                .setSuccess(true)
                .setServerCount(configLoader.currentConfigs().size())
                .setToolCount(tools.size())
                .build());
    }

    @Override
    public Uni<ListMcpToolsResponse> listTools(Empty request) {
        List<McpToolInfo> list = skillBridge.activeTools().stream()
            .map(tool -> {
                String schemaJson = "{}";
                try {
                    schemaJson = mapper.writeValueAsString(tool.inputSchema());
                } catch (Exception ignored) {}

                return McpToolInfo.newBuilder()
                    .setServerId(tool.serverId())
                    .setName(tool.name())
                    .setDescription(tool.description())
                    .setInputSchemaJson(schemaJson)
                    .build();
            })
            .toList();

        return Uni.createFrom().item(ListMcpToolsResponse.newBuilder()
            .addAllTools(list)
            .build());
    }

    private McpServerInfo toServerInfo(McpServerConfig cfg) {
        return McpServerInfo.newBuilder()
            .setId(cfg.id())
            .setUrl(cfg.url() == null ? "" : cfg.url())
            .setCommand(cfg.command() == null ? "" : cfg.command())
            .addAllArgs(cfg.args())
            .setTransport(cfg.transportType().name().toLowerCase())
            .setEnabled(cfg.enabled())
            .build();
    }
}
