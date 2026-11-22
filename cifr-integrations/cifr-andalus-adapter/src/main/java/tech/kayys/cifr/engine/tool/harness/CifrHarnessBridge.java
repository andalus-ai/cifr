package tech.kayys.cifr.engine.tool.harness;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.tool.CifrTool;
import tech.kayys.cifr.engine.tool.ToolCapability;
import tech.kayys.cifr.engine.tool.ToolRisk;
import tech.kayys.andalus.harness.capability.DefaultCapabilityScope;
import tech.kayys.andalus.harness.execution.action.InMemoryActionJournal;
import tech.kayys.andalus.harness.governance.budget.BudgetManager;
import tech.kayys.andalus.harness.governance.budget.DefaultBudgetLedger;
import tech.kayys.andalus.harness.governance.budget.DefaultBudgetManager;
import tech.kayys.andalus.harness.governance.budget.DefaultBudgetPolicy;
import tech.kayys.andalus.harness.tool.DefaultToolCatalog;
import tech.kayys.andalus.harness.tool.DefaultToolResolver;
import tech.kayys.andalus.harness.tool.GovernedToolExecutor;
import tech.kayys.andalus.harness.tool.ToolArguments;
import tech.kayys.andalus.harness.tool.ToolCatalog;
import tech.kayys.andalus.harness.tool.ToolInputValidator;
import tech.kayys.andalus.harness.tool.ToolIntent;
import tech.kayys.andalus.harness.tool.ToolIntentId;
import tech.kayys.andalus.harness.tool.ToolOutputValidator;
import tech.kayys.andalus.harness.tool.ToolRequestContext;
import tech.kayys.andalus.harness.tool.ToolResolver;
import tech.kayys.andalus.harness.tool.ToolResult;
import tech.kayys.andalus.harness.tool.ToolResultRouter;

import java.util.Map;
import java.util.Set;

/**
 * CDI singleton wiring Cifr tools into the Andalus governed execution pipeline.
 *
 * <p>Arbitrary code execution and network-capable tools are deliberately not
 * registered here. The current bridge has no per-invocation approval provider
 * and no OS-isolated execution backend; an operator allowlist alone is not
 * sufficient authorization to execute untrusted code.</p>
 */
@ApplicationScoped
public class CifrHarnessBridge {

    private static final Logger LOG = Logger.getLogger(CifrHarnessBridge.class);

    @Inject
    Instance<CifrTool> cifrTools;

    private GovernedToolExecutor governedExecutor;
    private BudgetManager budgetManager;
    private final InMemoryActionJournal journal = new InMemoryActionJournal();

    @PostConstruct
    void init() {
        ToolCatalog catalog = new DefaultToolCatalog();

        int count = 0;
        for (CifrTool tool : cifrTools) {
            if (requiresExplicitApproval(tool)) {
                LOG.warnf("Not registering high-risk Cifr tool until an approval and isolation provider exists: %s",
                        tool.descriptor().name());
                continue;
            }
            CifrToolProvider provider = new CifrToolProvider(tool);
            catalog.register(provider);
            LOG.infof("Registered Cifr tool in harness catalog: %s", tool.descriptor().name());
            count++;
        }
        LOG.infof("CifrHarnessBridge initialized with %d eligible tools", count);

        ToolResolver resolver = new DefaultToolResolver(catalog);

        String configuredTools = System.getProperty("cifr.harness.allowed-tools");
        if (configuredTools == null || configuredTools.isBlank()) {
            configuredTools = System.getenv("CIFR_HARNESS_ALLOWED_TOOLS");
        }
        Set<String> allowedTools = parseAllowedTools(configuredTools);
        DefaultCapabilityScope capScope = DefaultCapabilityScope.of(allowedTools);
        if (allowedTools.isEmpty()) {
            LOG.warn("CIFR harness has no allowed tools configured; governed executions will be denied");
        } else {
            LOG.infof("CIFR harness explicitly enabled tools: %s", allowedTools);
        }

        DefaultBudgetLedger ledger = new DefaultBudgetLedger();
        DefaultBudgetPolicy budgetPolicy = new DefaultBudgetPolicy(ledger);
        this.budgetManager = new DefaultBudgetManager(ledger, budgetPolicy);

        this.governedExecutor = new GovernedToolExecutor(
                catalog,
                resolver,
                capScope,
                null,
                budgetManager,
                null,
                journal,
                null,
                ToolInputValidator.standard(),
                ToolOutputValidator.standard(),
                ToolResultRouter.standard(),
                null
        );
    }

    /**
     * The bridge currently has no trusted per-call approval contract or isolated
     * process backend. Deny tools capable of executing arbitrary code, reaching
     * the network, or carrying a high/critical risk classification.
     */
    static boolean requiresExplicitApproval(CifrTool tool) {
        if (tool == null || tool.descriptor() == null) {
            return true;
        }
        var descriptor = tool.descriptor();
        Set<ToolCapability> capabilities = descriptor.capabilities();
        return descriptor.risk() == null
                || descriptor.risk() == ToolRisk.HIGH
                || descriptor.risk() == ToolRisk.CRITICAL
                || (capabilities != null && (capabilities.contains(ToolCapability.EXECUTE_PROCESS)
                || capabilities.contains(ToolCapability.NETWORK_ACCESS)));
    }

    static Set<String> parseAllowedTools(String configured) {
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public ToolResult executeGoverned(
            String toolName,
            Map<String, Object> arguments,
            HarnessToolExecutionContext ctx) {
        ToolArguments toolArgs = ToolArguments.of(arguments);
        ToolIntent intent = new ToolIntent(
                ToolIntentId.generate(),
                toolName,
                toolArgs,
                new ToolRequestContext(null, null, Map.of())
        );
        return governedExecutor.execute(intent, ctx, null);
    }

    public InMemoryActionJournal journal() {
        return journal;
    }

    public BudgetManager budgetManager() {
        return budgetManager;
    }

    public boolean isReady() {
        return governedExecutor != null;
    }
}
