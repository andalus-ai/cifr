package tech.kayys.cifr.engine.orchestrator.policy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.orchestrator.ResolvedTool;
import tech.kayys.cifr.engine.orchestrator.ToolIntent;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DefaultToolPolicy implements ToolPolicy {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "sudo", "su", "mkfs", "dd", "shutdown", "reboot", "poweroff"
    );

    @Override
    public Uni<ToolPolicyDecision> evaluate(ToolIntent intent, ResolvedTool resolved, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            Path root = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();

            // 1. Boundary check: Working directory must reside within workspaceRoot
            Path workDir = resolved.workingDirectory() != null ? resolved.workingDirectory().toAbsolutePath().normalize() : root;
            if (!workDir.startsWith(root)) {
                return ToolPolicyDecision.deny("Execution working directory outside workspace boundary: " + workDir);
            }

            // 2. Destructive command check
            List<String> cmd = resolved.command();
            if (!cmd.isEmpty()) {
                String binary = Path.of(cmd.get(0)).getFileName().toString();
                if (BLOCKED_COMMANDS.contains(binary)) {
                    return ToolPolicyDecision.deny("Disallowed administrative or destructive command: " + binary);
                }

                String fullCmd = String.join(" ", cmd);
                if (fullCmd.contains("rm -rf /") || fullCmd.contains(":(){ :|:& };:")) {
                    return ToolPolicyDecision.deny("Disallowed destructive shell pattern");
                }
            }

            // 3. Network Policy check
            if (resolved.environment().networkPolicy() == NetworkPolicy.ALLOW_REQUIRED) {
                return ToolPolicyDecision.requireApproval("Tool execution requires network access", ExecutionBudget.defaultBudget());
            }

            // 4. Assign budget based on tool kind
            ExecutionBudget budget = switch (intent.kind()) {
                case RUN_TEST, RUN_TESTS -> ExecutionBudget.testBudget();
                case BUILD -> ExecutionBudget.defaultBudget();
                default -> ExecutionBudget.quickBudget();
            };

            return ToolPolicyDecision.allow(budget);
        });
    }
}
