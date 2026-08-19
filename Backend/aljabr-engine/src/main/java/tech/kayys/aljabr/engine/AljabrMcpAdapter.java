package tech.kayys.aljabr.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.wayang.tool.mcp.HttpMcpToolClient;
import tech.kayys.wayang.tool.mcp.HttpMcpToolDiscoveryClient;
import tech.kayys.wayang.tool.mcp.McpDiscoveredTool;

import tech.kayys.wayang.tool.mcp.McpTool;
import tech.kayys.wayang.tool.mcp.McpToolDiscoveryRequest;
import tech.kayys.wayang.tool.mcp.McpToolDiscoveryResult;
import tech.kayys.wayang.tools.spi.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AljabrMcpAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<Tool> discoverMcpTools(Path workspaceDir) {
        List<Tool> tools = new ArrayList<>();
        try {
            Path current = workspaceDir.toAbsolutePath().normalize();
            Path configPath = null;

            while (current != null) {
                Path mcpJson = current.resolve("mcp.json");
                Path mcpServers = current.resolve(".mcp").resolve("servers.json");
                Path agentMcp = current.resolve(".agents").resolve("mcp.json");

                if (Files.isRegularFile(mcpJson)) {
                    configPath = mcpJson;
                    break;
                }
                if (Files.isRegularFile(mcpServers)) {
                    configPath = mcpServers;
                    break;
                }
                if (Files.isRegularFile(agentMcp)) {
                    configPath = agentMcp;
                    break;
                }

                if (Files.exists(current.resolve(".git")) || current.getParent() == null) {
                    break;
                }
                current = current.getParent();
            }

            if (configPath == null) {
                Path globalMcp = Path.of(System.getProperty("user.home"), ".wayang", "mcp.json");
                if (Files.isRegularFile(globalMcp)) {
                    configPath = globalMcp;
                }
            }

            String muqabalaEnv = System.getenv("MUQABALAH_MCP_ENDPOINT");
            if (muqabalaEnv == null || muqabalaEnv.isBlank()) {
                muqabalaEnv = System.getProperty("muqabala.mcp.endpoint");
            }
            if (muqabalaEnv == null || muqabalaEnv.isBlank()) {
                muqabalaEnv = "http://localhost:8080/mcp/sse";
            }

            if (configPath == null && muqabalaEnv.isBlank()) {
                return tools;
            }

            HttpMcpToolDiscoveryClient discoveryClient = new HttpMcpToolDiscoveryClient();
            HttpMcpToolClient mcpClient = new HttpMcpToolClient();

            boolean muqabalaFound = false;

            if (configPath != null) {
                JsonNode root = MAPPER.readTree(configPath.toFile());
                JsonNode servers = root.path("mcpServers");
                if (servers.isObject()) {
                    servers.fields().forEachRemaining(entry -> {
                        String serverName = entry.getKey();
                        JsonNode serverConfig = entry.getValue();

                        String endpoint = serverConfig.path("endpoint").asText(null);
                        if (endpoint == null) {
                            return; // Stdio not supported natively here yet
                        }

                        Map<String, Object> context = new HashMap<>();
                        context.put("mcpEndpoint", endpoint);

                        JsonNode envNode = serverConfig.path("env");
                        if (envNode.isObject()) {
                            envNode.fields().forEachRemaining(e -> {
                                // pass headers or env if needed
                            });
                        }

                        McpToolDiscoveryRequest request = new McpToolDiscoveryRequest(serverName, endpoint, context);
                        try {
                            McpToolDiscoveryResult result = discoveryClient.discoverTools(request).await()
                                    .indefinitely();
                            if (result.success() && result.tools() != null) {
                                for (McpDiscoveredTool dt : result.tools()) {
                                    tools.add(new McpTool(dt.id(), dt.name(), dt.description(), dt.inputSchema(),
                                            context, mcpClient));
                                }
                            } else {
                                System.err.println("[AljabrMcpAdapter] Failed to discover MCP tools for server "
                                        + serverName + ": " + result.error());
                            }
                        } catch (Exception e) {
                            System.err.println("[AljabrMcpAdapter] Error discovering MCP tools for " + serverName + ": "
                                    + e.getMessage());
                        }
                    });

                    if (servers.has("muqabala")) {
                        muqabalaFound = true;
                    }
                }
            }

            if (!muqabalaFound && muqabalaEnv != null && !muqabalaEnv.isBlank()) {
                String serverName = "muqabala";
                String endpoint = muqabalaEnv;
                Map<String, Object> context = new HashMap<>();
                context.put("mcpEndpoint", endpoint);

                McpToolDiscoveryRequest request = new McpToolDiscoveryRequest(serverName, endpoint, context);
                try {
                    McpToolDiscoveryResult result = discoveryClient.discoverTools(request).await().indefinitely();
                    if (result.success() && result.tools() != null) {
                        for (McpDiscoveredTool dt : result.tools()) {
                            tools.add(new McpTool(dt.id(), dt.name(), dt.description(), dt.inputSchema(), context,
                                    mcpClient));
                        }
                    } else {
                        System.err.println("[AljabrMcpAdapter] Failed to discover MCP tools for server " + serverName
                                + ": " + result.error());
                    }
                } catch (Exception e) {
                    System.err.println(
                            "[AljabrMcpAdapter] Error discovering MCP tools for " + serverName + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[AljabrMcpAdapter] Failed to load MCP config: " + e.getMessage());
        }
        return tools;
    }
}
