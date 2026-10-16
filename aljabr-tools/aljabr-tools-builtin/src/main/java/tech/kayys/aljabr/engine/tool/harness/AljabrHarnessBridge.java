package tech.kayys.aljabr.engine.tool.harness;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.tool.AljabrTool;
import tech.kayys.aljabr.engine.tool.ToolCapability;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * CDI singleton that wires all {@link AljabrTool} implementations into the Andalus Harness
 * {@link GovernedToolExecutor} pipeline.
 *
 * <p>At startup this bridge:
 * <ol>
 *   <li>Discovers every {@code @ApplicationScoped} {@link AljabrTool} via CDI.</li>
 *   <li>Wraps each one as an {@link AljabrToolProvider} and registers it in a {@link ToolCatalog}.</li>
 *   <li>Builds a {@link GovernedToolExecutor} with:
 *       capability scope (allow-all by default), input/output validation,
 *       budget tracking via {@link BudgetManager}, and an in-memory {@link InMemoryActionJournal} for auditability.</li>
 * </ol>
 *
 * <p>Tools that are registered here can be executed through the governed pipeline via
 * {@link #executeGoverned(String, Map, HarnessToolExecutionContext)}.
 * The JShell and JBang tools call this method to gain capability checking, budget governance, audit logging,
 * and output validation without changing their own {@link AljabrTool} implementation.
 * </p>
 */
@ApplicationScoped
public class AljabrHarnessBridge {

    private static final Logger LOG = Logger.getLogger(AljabrHarnessBridge.class);

    @Inject
    Instance<AljabrTool> aljabrTools;

    private GovernedToolExecutor governedExecutor;
    private BudgetManager budgetManager;
    private final InMemoryActionJournal journal = new InMemoryActionJournal();

    @PostConstruct
    void init() {
        ToolCatalog catalog = new DefaultToolCatalog();

        // Register all discovered Aljabr tools in the catalog
        int count = 0;
        for (AljabrTool tool : aljabrTools) {
            AljabrToolProvider provider = new AljabrToolProvider(tool);
            catalog.register(provider);
            LOG.infof("Registered Aljabr tool in harness catalog: %s", tool.descriptor().name());
            count++;
        }
        LOG.infof("AljabrHarnessBridge initialized with %d tools", count);

        // Build resolver (capability-name and tool-id matching)
        ToolResolver resolver = new DefaultToolResolver(catalog);

        // Capability scope: allow all Aljabr capability names by default
        Set<String> allCaps = StreamSupport.stream(aljabrTools.spliterator(), false)
                .filter(t -> t.descriptor().capabilities() != null)
                .flatMap(t -> t.descriptor().capabilities().stream())
                .map(ToolCapability::name)
                .collect(Collectors.toUnmodifiableSet());

        DefaultCapabilityScope capScope = allCaps.isEmpty()
                ? DefaultCapabilityScope.allowAll()
                : DefaultCapabilityScope.allowAll(); // keep allow-all; future: tighten per policy

        // Budget manager: track and govern tool calls
        DefaultBudgetLedger ledger = new DefaultBudgetLedger();
        DefaultBudgetPolicy budgetPolicy = new DefaultBudgetPolicy(ledger);
        this.budgetManager = new DefaultBudgetManager(ledger, budgetPolicy);

        this.governedExecutor = new GovernedToolExecutor(
                catalog,
                resolver,
                capScope,
                null,                            // no policy chain — allow-all
                budgetManager,                   // budget manager tracking tool calls
                null,                            // no approval gate
                journal,                         // in-memory audit journal
                null,                            // no memory store
                ToolInputValidator.standard(),   // standard schema validator
                ToolOutputValidator.standard(),  // standard output validator
                ToolResultRouter.standard(),     // standard result router
                null                             // default scheduler
        );
    }

    /**
     * Executes a tool through the full 10-step {@link GovernedToolExecutor} pipeline.
     *
     * @param toolName   the tool name (e.g., {@code "jshell"}, {@code "jbang"})
     * @param arguments  the tool arguments map
     * @param ctx        the harness execution context (identity, cancellation, resources)
     * @return the governed {@link ToolResult}
     */
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

    /**
     * Returns the action journal for observability and testing.
     */
    public InMemoryActionJournal journal() {
        return journal;
    }

    /**
     * Returns the budget manager for tool invocation budget tracking and limiting.
     */
    public BudgetManager budgetManager() {
        return budgetManager;
    }

    /**
     * Checks whether the bridge has been initialized and is ready to execute tools.
     */
    public boolean isReady() {
        return governedExecutor != null;
    }
}
