package tech.kayys.aljabr.engine.tool.repl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.tool.*;
import tech.kayys.aljabr.engine.tool.harness.AljabrHarnessBridge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Aljabr tool for interactive Java REPL and snippet evaluation via JShell.
 *
 * <p>Registered in {@link AljabrHarnessBridge} at startup so that all JShell evaluations
 * pass through the Andalus Harness capability scope and action journal.</p>
 */
@ApplicationScoped
public class JShellTool implements AljabrTool {

    private static final int DEFAULT_TIMEOUT_MS = 10_000;

    @Inject
    AljabrHarnessBridge harnessBridge;

    private JShellImpl sharedSession;
    private final Object sessionLock = new Object();


    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "jshell",
                "Evaluate Java code interactively using JShell. Supports expressions, statements, methods, and classes.",
                ToolCategory.SHELL,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "code", Map.of("type", "string", "description", "Java snippet(s) to evaluate"),
                                "session", Map.of("type", "boolean", "description", "Persist state across calls (default false)"),
                                "timeout", Map.of("type", "integer", "description", "Max eval time ms (default 10000)")
                        ),
                        "required", List.of("code")
                ),
                Set.of(ToolCapability.READ_ONLY),
                ToolRisk.LOW
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            Map<String, Object> args = request.arguments();
            String code = (String) args.get("code");
            if (code == null || code.isBlank()) {
                return JShellResult.err("Argument 'code' is required and must not be blank", null);
            }

            boolean useSession = Boolean.TRUE.equals(args.get("session"));
            int timeoutMs = args.containsKey("timeout") && args.get("timeout") instanceof Number n
                    ? n.intValue()
                    : DEFAULT_TIMEOUT_MS;

            try {
                JShellImpl.Result result;
                if (useSession) {
                    result = evaluateInSharedSession(code, timeoutMs);
                } else {
                    result = evaluateInFreshSession(code, timeoutMs);
                }

                if (result.success()) {
                    return JShellResult.ok(result.output(), result.stdout());
                } else {
                    return JShellResult.err(result.error(), result.stdout());
                }
            } catch (Exception ex) {
                return JShellResult.err("JShell error: " + ex.getMessage(), null);
            }
        });
    }

    private JShellImpl.Result evaluateInFreshSession(String code, int timeoutMs) {
        try (JShellImpl jshell = new JShellImpl()) {
            return withTimeout(() -> jshell.evaluate(code), timeoutMs);
        }
    }

    private JShellImpl.Result evaluateInSharedSession(String code, int timeoutMs) {
        synchronized (sessionLock) {
            if (sharedSession == null) {
                sharedSession = new JShellImpl();
            }
            return withTimeout(() -> sharedSession.evaluate(code), timeoutMs);
        }
    }

    private JShellImpl.Result withTimeout(java.util.concurrent.Callable<JShellImpl.Result> task, int timeoutMs) {
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                return JShellImpl.Result.failure("Internal error: " + e.getMessage());
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            return JShellImpl.Result.failure("Evaluation timed out after " + timeoutMs + " ms");
        } catch (Exception e) {
            return JShellImpl.Result.failure("Evaluation error: " + e.getMessage());
        }
    }
}
