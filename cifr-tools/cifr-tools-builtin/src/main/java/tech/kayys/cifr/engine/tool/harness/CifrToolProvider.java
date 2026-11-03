package tech.kayys.cifr.engine.tool.harness;

import tech.kayys.cifr.engine.tool.CifrTool;
import tech.kayys.cifr.engine.tool.ToolCapability;
import tech.kayys.cifr.engine.tool.ToolDescriptor;
import tech.kayys.cifr.engine.tool.ToolRequest;
import tech.kayys.cifr.engine.tool.ToolResult;
import tech.kayys.andalus.harness.tool.CancellationToken;
import tech.kayys.andalus.harness.tool.ToolCatalog;
import tech.kayys.andalus.harness.tool.ToolExecutionContext;
import tech.kayys.andalus.harness.tool.ToolExecutionProfile;
import tech.kayys.andalus.harness.tool.ToolId;
import tech.kayys.andalus.harness.tool.ToolInputSchema;
import tech.kayys.andalus.harness.tool.ToolInvocation;
import tech.kayys.andalus.harness.tool.ToolInvocationId;
import tech.kayys.andalus.harness.tool.ToolKind;
import tech.kayys.andalus.harness.tool.ToolLogger;
import tech.kayys.andalus.harness.tool.ToolOutputSchema;
import tech.kayys.andalus.harness.tool.ToolProvider;
import tech.kayys.andalus.harness.tool.SchemaProperty;
import tech.kayys.andalus.harness.tool.SchemaType;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapts a single {@link CifrTool} into the Andalus Harness {@link ToolProvider} contract.
 *
 * <p>This allows Cifr tools to be registered in the harness {@link ToolCatalog} and
 * executed through the full {@code GovernedToolExecutor} pipeline (capability check,
 * policy, budget, action journal, output validation).</p>
 */
public class CifrToolProvider implements ToolProvider {

    private final CifrTool cifrTool;
    private final HarnessToolDescriptor harnessDescriptor;
    private final ToolId toolId;

    public CifrToolProvider(CifrTool cifrTool) {
        this.cifrTool = cifrTool;
        ToolDescriptor d = cifrTool.descriptor();
        this.toolId = ToolId.of(d.name());
        this.harnessDescriptor = new HarnessToolDescriptor(
                toolId,
                d.name(),
                "1.0",
                mapCapabilities(d),
                anyInput(),
                anyOutput(),
                deriveProfile(d)
        );
    }

    @Override
    public Optional<tech.kayys.andalus.harness.tool.ToolDescriptor> describe(ToolId id) {
        return toolId.equals(id) ? Optional.of(harnessDescriptor) : Optional.empty();
    }

    @Override
    public Collection<tech.kayys.andalus.harness.tool.ToolDescriptor> tools() {
        return List.of(harnessDescriptor);
    }

    @Override
    public tech.kayys.andalus.harness.tool.ToolExecutor executor(ToolId id) {
        return new CifrToolExecutor(cifrTool);
    }

    // ---- Helpers ----

    private Set<String> mapCapabilities(ToolDescriptor d) {
        if (d.capabilities() == null) return Set.of();
        return d.capabilities().stream()
                .map(ToolCapability::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private ToolExecutionProfile deriveProfile(ToolDescriptor d) {
        Set<?> caps = d.capabilities();
        if (caps == null || caps.isEmpty()) {
            return ToolExecutionProfile.readOnly(ToolKind.COMPUTE);
        }
        boolean mutates = caps.contains(ToolCapability.WRITE)
                || caps.contains(ToolCapability.DELETE)
                || caps.contains(ToolCapability.MUTATES_WORKSPACE)
                || caps.contains(ToolCapability.MUTATES_GIT);
        boolean process = caps.contains(ToolCapability.EXECUTE_PROCESS);
        ToolKind kind = process ? ToolKind.PROCESS
                : mutates ? ToolKind.WRITE
                : ToolKind.READ;
        return mutates ? ToolExecutionProfile.modifying(kind)
                : ToolExecutionProfile.readOnly(kind);
    }

    private static ToolInputSchema anyInput() {
        return new ToolInputSchema() {
            @Override public SchemaType rootType() { return SchemaType.OBJECT; }
            @Override public Map<String, SchemaProperty> properties() { return Map.of(); }
            @Override public Set<String> required() { return Set.of(); }
        };
    }

    private static ToolOutputSchema anyOutput() {
        return new ToolOutputSchema() {
            @Override public SchemaType rootType() { return SchemaType.OBJECT; }
            @Override public Map<String, SchemaProperty> properties() { return Map.of(); }
        };
    }

    // ---- Inner: harness ToolDescriptor impl ----

    private record HarnessToolDescriptor(
            ToolId id,
            String name,
            String version,
            Set<String> capabilities,
            ToolInputSchema inputSchema,
            ToolOutputSchema outputSchema,
            ToolExecutionProfile executionProfile
    ) implements tech.kayys.andalus.harness.tool.ToolDescriptor {}

    // ---- Inner: ToolExecutor bridge ----

    private static class CifrToolExecutor implements tech.kayys.andalus.harness.tool.ToolExecutor {

        private final CifrTool tool;

        CifrToolExecutor(CifrTool tool) {
            this.tool = tool;
        }

        @Override
        public tech.kayys.andalus.harness.tool.ToolResult execute(
                ToolInvocation invocation,
                ToolExecutionContext ctx) {

            // Bridge harness ToolArguments → Cifr arguments map
            Map<String, Object> args = invocation.arguments() != null
                    ? invocation.arguments().asMap()
                    : Map.of();

            String taskId = ctx != null && ctx.executionId() != null
                    ? ctx.executionId().value()
                    : ToolInvocationId.generate().value();

            ToolRequest request = new ToolRequest(
                    tool.descriptor().name(),
                    args,
                    Optional.of("Harness-governed execution"),
                    List.of()
            );

            String workspaceId = "workspace-unavailable";
            java.nio.file.Path workspaceRoot = null;
            Runnable cancellation = () -> {};
            if (ctx instanceof HarnessToolExecutionContext harnessContext) {
                if (harnessContext.workspaceId() != null && !harnessContext.workspaceId().isBlank()) {
                    workspaceId = harnessContext.workspaceId();
                }
                workspaceRoot = harnessContext.workspaceRoot().orElse(null);
                cancellation = () -> {
                    if (harnessContext.cancellation() != null) {
                        harnessContext.cancellation().cancel();
                    }
                };
            }
            tech.kayys.cifr.engine.tool.ToolExecutionContext cifrCtx =
                    tech.kayys.cifr.engine.tool.ToolExecutionContext.of(
                            taskId,
                            workspaceId,
                            null,
                            Optional.empty(),
                            cancellation,
                            workspaceRoot
                    );

            // Execute (block on Uni — harness ToolExecutor is synchronous at this layer)
            ToolResult cifrResult;
            try {
                cifrResult = tool.execute(request, cifrCtx).await().indefinitely();
            } catch (Exception e) {
                return tech.kayys.andalus.harness.tool.ToolResult.failure(
                        invocation.id(),
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                        Duration.ZERO,
                        tool.descriptor().name()
                );
            }

            // Bridge Cifr ToolResult → harness ToolResult
            if (cifrResult.isSuccess()) {
                return tech.kayys.andalus.harness.tool.ToolResult.success(
                        invocation.id(),
                        cifrResult.getSummary(),
                        Duration.ZERO,
                        tool.descriptor().name()
                );
            } else {
                return tech.kayys.andalus.harness.tool.ToolResult.failure(
                        invocation.id(),
                        cifrResult.getSummary(),
                        Duration.ZERO,
                        tool.descriptor().name()
                );
            }
        }
    }
}
