package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.ToolKind;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool adapter that bridges semantic code intents (symbol search, references,
 * AST definitions, diagnostics, formatting) to auto-provisioned LSP servers.
 */
@ApplicationScoped
public class MuqabalaToolAdapter implements ToolAdapter {

    @Inject
    LspProvisioningService lspProvisioner;

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return !toolchain.languages().isEmpty();
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        String primaryLang = toolchain.languages().iterator().next();

        return lspProvisioner.ensureLspInstalled(primaryLang)
            .map(lspPath -> {
                List<String> cmd = new ArrayList<>();
                cmd.add(lspPath.toString());

                if (intent.arguments().containsKey("file")) {
                    cmd.add((String) intent.arguments().get("file"));
                }

                return new ResolvedTool(
                    intent.kind(),
                    cmd,
                    toolchain.rootDirectory(),
                    ExecutionEnvironment.defaultSandboxed()
                );
            })
            .onFailure().recoverWithItem(() -> {
                return new ResolvedTool(
                    intent.kind(),
                    List.of(primaryLang),
                    toolchain.rootDirectory(),
                    ExecutionEnvironment.defaultSandboxed()
                );
            });
    }
}
