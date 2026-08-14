package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import tech.kayys.batuta.runtime.spi.RuntimeEngine;
import tech.kayys.batuta.runtime.spi.ContextEngine;
import tech.kayys.batuta.chromium.ChromiumBrowserEngineFactory;
import tech.kayys.batuta.mcp.session.McpBrowserSession;

import tech.kayys.batuta.api.options.BrowserOptions;
import tech.kayys.batuta.api.options.BrowserType;
import tech.kayys.batuta.api.options.LaunchMode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@ApplicationScoped
public class WebSearchSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("query", "The search query string");
    }


    private static final Logger LOG = Logger.getLogger(WebSearchSkill.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private RuntimeEngine runtime;
    private ContextEngine contextEngine;
    private McpBrowserSession session;

    

    

    

    

    

    

    private synchronized void initBrowser() {
        if (runtime == null) {
            LOG.info("Initializing Batuta Chromium Runtime for Web Search...");
            BrowserOptions options = BrowserOptions.builder(BrowserType.CHROMIUM)
                    .launchMode(LaunchMode.HEADLESS)
                    .build();

            runtime = new ChromiumBrowserEngineFactory().launch(options);
            contextEngine = runtime.newContext(options);
            session = new McpBrowserSession(contextEngine, contextEngine.newPage());
        }
    }

    @PreDestroy
    void cleanup() {
        if (runtime != null) {
            try {
                runtime.shutdown();
            } catch (Exception e) {
                LOG.error("Error closing Batuta runtime", e);
            }
        }
    }

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String query = (String) invocation.arguments().get("query");

        return Uni.createFrom().<ToolResult>item(() -> {
            try {
                if (query == null || query.isBlank()) {
                    return (ToolResult) AljabrToolResult.error("query parameter is required");
                }
                initBrowser();

                String searchUrl = "https://html.duckduckgo.com/html/?q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8);
                session.navigate(searchUrl, TIMEOUT);

                // Get the text content of the results
                String content = session.textContent(".results", TIMEOUT);
                if (content == null || content.isBlank()) {
                    content = session.accessibilityTree(-1); // Fallback to accessibility tree if .results is empty
                }

                return (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "content", content));
            } catch (Exception e) {
                LOG.error("Batuta browser error during search", e);
                return (ToolResult) AljabrToolResult.error(e.getMessage());
            }
        }).subscribeAsCompletionStage();
    }
}
