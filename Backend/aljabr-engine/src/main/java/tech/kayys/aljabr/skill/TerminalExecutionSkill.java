package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.wayang.spi.sandbox.Sandbox;
import tech.kayys.wayang.spi.sandbox.SandboxConfiguration;
import tech.kayys.wayang.spi.sandbox.SandboxExecutionResult;
import tech.kayys.wayang.spi.sandbox.SandboxProvider;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;
import jakarta.enterprise.inject.Instance;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class TerminalExecutionSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("Terminal Execution Sandbox", "Executes stateful shell commands within a secure sandbox (Docker or WASM) to prevent host modification.");
    }


    private static final Logger LOG = Logger.getLogger(TerminalExecutionSkill.class);

    @Inject
    Instance<SandboxProvider> sandboxProviders;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String command = (String) invocation.arguments().get("command");
        if (command == null || command.isBlank()) {
            return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Input 'command' is required")).subscribeAsCompletionStage();
        }

        String workdir = invocation.arguments().containsKey("workdir") ? (String) invocation.arguments().get("workdir") : "/workspace";
        int timeoutSec = invocation.arguments().containsKey("timeout_seconds") ? ((Number) invocation.arguments().get("timeout_seconds")).intValue()
                : 30;
        String strategyName = (String) invocation.arguments().getOrDefault("strategy", "nono"); // Default to Nono

        SandboxProvider selectedProvider = null;
        for (SandboxProvider provider : sandboxProviders) {
            if (provider.getProviderId().equalsIgnoreCase(strategyName)) {
                selectedProvider = provider;
                break;
            }
        }

        if (selectedProvider == null) {
            return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Unsupported sandbox strategy: " + strategyName)).subscribeAsCompletionStage();
        }

        SandboxProvider provider = selectedProvider; // Final for lambda

        return Uni.createFrom().item(() -> {
            try {
                SandboxConfiguration config = new SandboxConfiguration();
                config.setWorkingDirectory(workdir);
                
                try (Sandbox sandbox = provider.createSandbox(config)) {
                    sandbox.start();
                    SandboxExecutionResult result = sandbox.executeCommand(command, timeoutSec * 1000L);
                    
                    Map<String, Object> output = new LinkedHashMap<>();
                    output.put("stdout", result.stdout());
                    output.put("stderr", result.stderr());
                    output.put("exit_code", result.exitCode());
                    
                    if (result.isSuccess()) {
                        output.put("status", "success");
                        output.put("result", result.stdout().isBlank() ? "(no output)" : result.stdout());
                    } else {
                        output.put("status", "error");
                        output.put("message", "Process exited with code " + result.exitCode() + ": " + result.stderr());
                    }
                    return (ToolResult) AljabrToolResult.successMap(output);
                }
            } catch (Exception e) {
                LOG.error("Execution failed in sandbox", e);
                return (ToolResult) AljabrToolResult.error(e.getMessage());
            }
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool()).subscribeAsCompletionStage();
    }

    
}
