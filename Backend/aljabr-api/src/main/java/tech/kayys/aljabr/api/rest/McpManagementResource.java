package tech.kayys.aljabr.api.rest;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.wayang.agent.mcp.McpConfigLoader;
import tech.kayys.wayang.agent.mcp.McpServerConfig;
import tech.kayys.wayang.agent.mcp.McpSkillBridge;
import tech.kayys.wayang.agent.mcp.McpToolDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * REST API for viewing, registering, modifying, removing, and hot-reloading MCP servers.
 */
@Path("/api/mcp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class McpManagementResource {

    @Inject
    McpConfigLoader configLoader;

    @Inject
    McpSkillBridge skillBridge;

    @GET
    @Path("/servers")
    public Collection<McpServerConfig> listServers() {
        return configLoader.loadMergedConfigs().values();
    }

    @GET
    @Path("/tools")
    public List<McpToolDescriptor> listTools() {
        return skillBridge != null ? skillBridge.activeTools() : List.of();
    }

    @POST
    @Path("/servers")
    public Uni<Response> registerServer(Map<String, Object> body) {
        String id = (String) body.get("id");
        if (id == null || id.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Server id is required")).build());
        }

        McpServerConfig config = McpServerConfig.fromMap(id, body);
        return skillBridge.connect(config)
            .map(tools -> Response.status(Response.Status.CREATED)
                .entity(Map.of("server", config, "tools", tools))
                .build());
    }

    @PUT
    @Path("/servers/{id}")
    public Uni<Response> updateServer(@PathParam("id") String id, Map<String, Object> body) {
        McpServerConfig config = McpServerConfig.fromMap(id, body);
        return skillBridge.connect(config)
            .map(tools -> Response.ok(Map.of("server", config, "tools", tools)).build());
    }

    @DELETE
    @Path("/servers/{id}")
    public Uni<Response> removeServer(@PathParam("id") String id) {
        return skillBridge.disconnect(id)
            .map(v -> Response.ok(Map.of("success", true, "message", "Server " + id + " disconnected and removed")).build());
    }

    @POST
    @Path("/reload")
    public Uni<Response> reload() {
        return configLoader.reload()
            .map(tools -> Response.ok(Map.of(
                "success", true,
                "serverCount", configLoader.currentConfigs().size(),
                "toolCount", tools.size(),
                "tools", tools
            )).build());
    }
}
