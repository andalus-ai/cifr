package tech.kayys.aljabr.engine.orchestrator;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.orchestrator.adapter.DefaultToolResolver;
import tech.kayys.aljabr.engine.orchestrator.adapter.ToolResolver;
import tech.kayys.aljabr.engine.orchestrator.exec.DefaultExecutionEngine;
import tech.kayys.aljabr.engine.orchestrator.exec.ExecutionEngine;
import tech.kayys.aljabr.engine.orchestrator.exec.ExecutionResult;
import tech.kayys.aljabr.engine.orchestrator.output.ExecutionDiagnostic;
import tech.kayys.aljabr.engine.orchestrator.output.ExecutionFailureType;
import tech.kayys.aljabr.engine.orchestrator.output.ExecutionStatus;
import tech.kayys.aljabr.engine.orchestrator.output.ProcessedOutput;
import tech.kayys.aljabr.engine.orchestrator.policy.DefaultToolPolicy;
import tech.kayys.aljabr.engine.orchestrator.policy.ToolPolicy;
import tech.kayys.aljabr.engine.orchestrator.policy.ToolPolicyDecision;
import tech.kayys.aljabr.engine.orchestrator.toolchain.DefaultProjectDetector;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectDetector;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class DefaultToolOrchestrator implements ToolOrchestrator {

    private static final Logger LOG = Logger.getLogger(DefaultToolOrchestrator.class);

    @Inject
    ProjectDetector projectDetector;

    @Inject
    ToolResolver toolResolver;

    @Inject
    ToolPolicy toolPolicy;

    @Inject
    ExecutionEngine executionEngine;

    public DefaultToolOrchestrator() {
    }

    public DefaultToolOrchestrator(ProjectDetector projectDetector, ToolResolver toolResolver,
                                   ToolPolicy toolPolicy, ExecutionEngine executionEngine) {
        this.projectDetector = projectDetector;
        this.toolResolver = toolResolver;
        this.toolPolicy = toolPolicy;
        this.executionEngine = executionEngine;
    }

    @Override
    public Uni<ExecutionResult> executeIntent(ToolIntent intent, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            Path root = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
            ProjectDetector detector = projectDetector != null ? projectDetector : new DefaultProjectDetector();
            ToolResolver resolver = toolResolver != null ? toolResolver : new DefaultToolResolver(List.of());
            ToolPolicy policy = toolPolicy != null ? toolPolicy : new DefaultToolPolicy();
            ExecutionEngine engine = executionEngine != null ? executionEngine : new DefaultExecutionEngine();

            // 1. Toolchain Detection
            ProjectToolchain toolchain = detector.detect(root).await().indefinitely();
            LOG.infof("Detected toolchain %s for workspace %s", toolchain.buildSystem(), root);

            // 2. Semantic Intent Resolution
            ResolvedTool resolved = resolver.resolve(intent, toolchain).await().indefinitely();

            // 3. Safety Policy Evaluation
            ToolPolicyDecision decision = policy.evaluate(intent, resolved, root).await().indefinitely();
            if (!decision.allowed()) {
                LOG.warnf("Tool intent [%s] rejected by safety policy: %s", intent.id(), decision.reason());
                ProcessedOutput out = new ProcessedOutput(
                    "Policy violation: " + decision.reason(),
                    List.of(ExecutionDiagnostic.error(decision.reason(), "SECURITY")),
                    "Execution denied by safety gate.",
                    false,
                    ExecutionFailureType.PERMISSION_FAILURE
                );
                return new ExecutionResult(intent.id(), ExecutionStatus.BLOCKED, -1, Duration.ZERO, out);
            }

            // 4. Sandboxed Execution
            return engine.execute(intent.id(), resolved, decision.budget()).await().indefinitely();
        });
    }
}
