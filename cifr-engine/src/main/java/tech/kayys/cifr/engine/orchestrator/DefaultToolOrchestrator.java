package tech.kayys.cifr.engine.orchestrator;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.exec.ExecutionResult;
import tech.kayys.cifr.engine.orchestrator.adapter.ToolResolver;
import tech.kayys.cifr.engine.orchestrator.exec.ExecutionEngine;
import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectDetector;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

import java.nio.file.Path;

@ApplicationScoped
public class DefaultToolOrchestrator implements ToolOrchestrator {

    @Inject
    ProjectDetector projectDetector;

    @Inject
    ToolResolver resolver;

    @Inject
    ExecutionEngine executionEngine;

    public DefaultToolOrchestrator() {}

    public DefaultToolOrchestrator(ProjectDetector projectDetector, ToolResolver resolver, ExecutionEngine executionEngine) {
        this.projectDetector = projectDetector;
        this.resolver = resolver;
        this.executionEngine = executionEngine;
    }

    @Override
    public Uni<ExecutionResult> orchestrate(ToolIntent intent, Path workspaceRoot, ToolOrchestrationPolicy policy) {
        if (intent.kind() == ToolKind.RUN_COMMAND && !policy.allowArbitraryCommands()) {
            return Uni.createFrom().failure(new SecurityException("Arbitrary shell execution blocked by ToolOrchestrationPolicy"));
        }

        ProjectToolchain toolchain = projectDetector.detect(workspaceRoot);
        ExecutionBudget budget = new ExecutionBudget(policy.timeout(), policy.maxOutputBytes(), 512 * 1024 * 1024L, 4);

        return resolver.resolve(intent, toolchain)
            .flatMap(resolved -> executionEngine.execute(intent.id(), resolved, budget));
    }
}
