package tech.kayys.cifr.engine.tool.script;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.tool.*;
import tech.kayys.cifr.engine.tool.harness.CifrHarnessBridge;
import tech.kayys.cifr.engine.tool.harness.HarnessToolExecutionContext;

import java.nio.file.Path;
import java.util.*;

/**
 * Cifr tool for executing Java scripts and instant snippets with automatic dependency resolution via JBang.
 * Includes automatic self-bootstrapping/download if not installed on the host.
 *
 * <p>Execution is routed through {@link CifrHarnessBridge} for capability checking,
 * audit journaling, and output validation via the Andalus Harness {@code GovernedToolExecutor}.</p>
 */
@ApplicationScoped
public class JBangTool implements CifrTool {

    private static final Logger LOG = Logger.getLogger(JBangTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;

    @Inject
    CifrHarnessBridge harnessBridge;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "jbang",
                "Execute Java scripts, applications, and instant snippets with zero configuration and automated Maven dependency fetching via JBang. Automatically downloads JBang if missing.",
                ToolCategory.SHELL,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "code", Map.of("type", "string", "description", "Java code snippet or script to run instantly."),
                                "script_path", Map.of("type", "string", "description", "Path to an existing .java/.jsh file or remote URL to run."),
                                "dependencies", Map.of("type", "array", "description", "Maven coordinates for dependencies (e.g. ['org.apache.commons:commons-lang3:3.14.0']).",
                                        "items", Map.of("type", "string")),
                                "args", Map.of("type", "array", "description", "Arguments passed to the Java application.",
                                        "items", Map.of("type", "string")),
                                "jbang_args", Map.of("type", "array", "description", "Additional CLI flags passed to jbang.",
                                        "items", Map.of("type", "string")),
                                "action", Map.of("type", "string", "description", "Action to perform: 'run' (default), 'install' (verify/download jbang), 'info'.",
                                        "enum", List.of("run", "install", "info")),
                                "timeout", Map.of("type", "integer", "description", "Execution timeout in seconds (default 180).")
                        )
                ),
                Set.of(ToolCapability.EXECUTE_PROCESS, ToolCapability.NETWORK_ACCESS),
                ToolRisk.MEDIUM
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            Map<String, Object> args = request.arguments();
            String action = argString(args, "action", "run");
            int timeoutSec = argInt(args, "timeout", DEFAULT_TIMEOUT_SECONDS);

            Path workingDir = context.resolveWorkspacePath(".");
            if (args.containsKey("working_dir") && args.get("working_dir") instanceof String wdStr) {
                workingDir = context.resolveWorkspacePath(wdStr);
            }

            try {
                if ("install".equalsIgnoreCase(action)) {
                    Path installed = JBangBootstrap.ensureInstalled();
                    return JBangResult.ok(
                            "JBang successfully verified/installed at: " + installed.toAbsolutePath(),
                            "", "", 0
                    );
                }

                Path jbangPath = JBangBootstrap.ensureInstalled();
                JBangImpl runner = new JBangImpl(jbangPath);

                if ("info".equalsIgnoreCase(action)) {
                    JBangImpl.Result res = runner.executeSubcommand(List.of("version"), timeoutSec, workingDir);
                    return res.success()
                            ? JBangResult.ok(res.stdout(), res.stdout(), res.stderr(), res.exitCode())
                            : JBangResult.err(res.stderr(), res.stdout(), res.stderr(), res.exitCode());
                }

                String code = argString(args, "code", null);
                String scriptPath = argString(args, "script_path", null);
                List<String> deps = argStringList(args, "dependencies");
                List<String> scriptArgs = argStringList(args, "args");
                List<String> jbangFlags = argStringList(args, "jbang_args");

                JBangImpl.Result result;
                if (code != null && !code.isBlank()) {
                    result = runner.runSnippet(code, deps, scriptArgs, jbangFlags, timeoutSec, workingDir);
                } else if (scriptPath != null && !scriptPath.isBlank()) {
                    if (!scriptPath.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                        scriptPath = context.resolveWorkspacePath(scriptPath).toString();
                    }
                    result = runner.runScript(scriptPath, scriptArgs, jbangFlags, timeoutSec, workingDir);
                } else {
                    return JBangResult.err("Either 'code' or 'script_path' must be provided for 'run' action.", "", "", -1);
                }

                return result.success()
                        ? JBangResult.ok(result.combinedOutput(), result.stdout(), result.stderr(), result.exitCode())
                        : JBangResult.err(result.combinedOutput(), result.stdout(), result.stderr(), result.exitCode());

            } catch (Exception ex) {
                return JBangResult.err("JBang tool execution error: " + ex.getMessage(), "", ex.getMessage(), -1);
            }
        });
    }

    private static String argString(Map<String, Object> args, String key, String defaultValue) {
        Object v = args.get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private static int argInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignore) {}
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> argStringList(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
