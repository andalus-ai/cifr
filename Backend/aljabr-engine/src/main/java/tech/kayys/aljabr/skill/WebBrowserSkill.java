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

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

@ApplicationScoped
public class WebBrowserSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("action", "One of: navigate, click, fill, text_content, read_page, evaluate");
    }


    private static final Logger LOG = Logger.getLogger(WebBrowserSkill.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private RuntimeEngine runtime;
    private ContextEngine contextEngine;
    private McpBrowserSession session;

    

    

    

    

    

    

    private synchronized void initBrowser() {
        if (runtime == null) {
            LOG.info("Initializing Batuta Chromium Runtime (Headless)...");
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
        String action = (String) invocation.arguments().get("action");
        String url = (String) invocation.arguments().get("url");
        String selector = (String) invocation.arguments().get("selector");
        String text = (String) invocation.arguments().get("text");

        return Uni.createFrom().<ToolResult>item(() -> {
            try {
                if ("navigate".equalsIgnoreCase(action)) {
                    if (url == null || url.isBlank()) {
                        return AljabrToolResult.error("url is required for navigate");
                    }
                    initBrowser();
                    String finalUrl = session.navigate(url, TIMEOUT);
                    return AljabrToolResult.successMap(Map.<String, Object>of(
                            "status", "success",
                            "message", "Navigated to " + finalUrl,
                            "content", session.accessibilityTree(-1)));
                } else if ("click".equalsIgnoreCase(action)) {
                    if (selector == null || selector.isBlank()) {
                        return AljabrToolResult.error("selector is required for click");
                    }
                    initBrowser();
                    session.click(selector, TIMEOUT);
                    return AljabrToolResult.successMap(Map.<String, Object>of(
                            "status", "success",
                            "message", "Clicked " + selector,
                            "content", session.accessibilityTree(-1)));
                } else if ("fill".equalsIgnoreCase(action)) {
                    if (selector == null || selector.isBlank() || text == null) {
                        return AljabrToolResult.error("selector and text are required for fill");
                    }
                    initBrowser();
                    session.fill(selector, text, TIMEOUT);
                    return AljabrToolResult.successMap(Map.<String, Object>of(
                            "status", "success",
                            "message", "Filled text into " + selector));
                } else if ("read_page".equalsIgnoreCase(action)) {
                    initBrowser();
                    return AljabrToolResult.successMap(Map.<String, Object>of(
                            "status", "success",
                            "content", session.accessibilityTree(-1)));
                } else if ("text_content".equalsIgnoreCase(action)) {
                    if (selector == null || selector.isBlank()) {
                        return AljabrToolResult.error("selector is required for text_content");
                    }
                    initBrowser();
                    String content = session.textContent(selector, TIMEOUT);
                    return AljabrToolResult.successMap(Map.<String, Object>of(
                            "status", "success",
                            "content", content));
                } else if ("evaluate".equalsIgnoreCase(action)) {
                    if (text == null || text.isBlank()) {
                        return AljabrToolResult.error("text (expression) is required for evaluate");
                    }
                    initBrowser();
                    String result = session.evaluate(text);
                    return AljabrToolResult.successMap(Map.<String, Object>of(
                            "status", "success",
                            "content", result));
                } else {
                    return AljabrToolResult.error("Unknown action: " + action);
                }
            } catch (Exception e) {
                LOG.error("Batuta browser error during action " + action, e);
                return AljabrToolResult.error(e.getMessage());
            }
        }).subscribeAsCompletionStage();
    }
}
