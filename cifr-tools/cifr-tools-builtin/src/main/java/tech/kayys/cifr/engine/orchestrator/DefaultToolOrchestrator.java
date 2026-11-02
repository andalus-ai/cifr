package tech.kayys.cifr.engine.orchestrator;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.exec.ExecutionResult;
import tech.kayys.cifr.engine.orchestrator.adapter.ToolResolver;
import tech.kayys.cifr.engine.orchestrator.exec.ExecutionEngine;
import tech.kayys.cifr.engine.orchestrator.policy.DefaultToolPolicy;
import tech.kayys.cifr.engine.orchestrator.policy.ToolPolicy;
import tech.kayys.cifr.engine.orchestrator.policy.ToolPolicyDecision;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectDetector;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultToolOrchestrator implements ToolOrchestrator {

    private static final Logger log = Logger.getLogger(DefaultToolOrchestrator.class.getName());

    @Inject
    ToolResolver toolResolver;

    @Inject
    ExecutionEngine executionEngine;

    @Inject
    ProjectDetector projectDetector;

    @Inject
    ToolPolicy toolPolicy;

    @Override
    public Uni<ExecutionResult> orchestrate(ToolIntent intent, Path workspaceRoot, ToolOrchestrationPolicy policy) {
        return Uni.createFrom().deferred(() -> {
            if (workspaceRoot == null) {
                return Uni.createFrom().failure(new IllegalArgumentException("Workspace root cannot be null"));
            }

            // Detect project toolchain
            ProjectToolchain toolchain = projectDetector.detect(workspaceRoot);

            // Resolve the tool to an executable command
            return toolResolver.resolve(intent, toolchain)
                    .flatMap(resolved -> {
                        // Evaluate policy
                        return toolPolicy.evaluate(intent, resolved, workspaceRoot)
                                .flatMap(decision -> {
                                    if (!decision.allowed()) {
                                        return Uni.createFrom().item(new ExecutionResult(
                                                403,
                                                "Tool execution denied: " + decision.reason(),
                                                "",
                                                List.of(),
                                                java.util.Optional.empty(),
                                                false));
                                    }

                                    if (decision.requiresApproval()) {
                                        // In production, this would trigger an approval workflow
                                        log.info("Tool requires approval: " + decision.reason());
                                        // For now, proceed with approval
                                    }

                                    // Execute with the decision's budget or default
                                    var budget = decision.budget() != null
                                            ? decision.budget()
                                            : tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget
                                                    .defaultBudget();

                                    String executionId = "orchestrate-" + UUID.randomUUID().toString().substring(0, 8);
                                    return executionEngine.execute(executionId, resolved, budget);
                                });
                    });
        });
    }
}