package tech.kayys.cifr.engine.tool.script;

import tech.kayys.andalus.sandbox.LocalProcessSandbox;
import tech.kayys.andalus.spi.sandbox.SandboxConfiguration;
import tech.kayys.andalus.spi.sandbox.SandboxExecutionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Execution engine for JBang in Cifr.
 *
 * <p>Process execution is delegated to {@link LocalProcessSandbox} from {@code andalus-sandbox},
 * which provides:
 * <ul>
 *   <li>Path traversal protection (sandbox containment)</li>
 *   <li>Working-directory isolation</li>
 *   <li>Virtual-thread I/O capture</li>
 *   <li>Timeout management with forcible destroy</li>
 * </ul>
 */
public class JBangImpl {

    private final Path executable;

    public JBangImpl(Path executable) {
        this.executable = Objects.requireNonNull(executable, "jbang executable path must not be null");
    }

    public static JBangImpl auto() throws IOException, InterruptedException {
        Path path = JBangBootstrap.ensureInstalled();
        return new JBangImpl(path);
    }

    public Result runSnippet(
            String code,
            List<String> dependencies,
            List<String> scriptArgs,
            List<String> jbangOptions,
            int timeoutSec,
            Path workingDir
    ) throws IOException, InterruptedException {
        if (code == null || code.isBlank()) {
            return Result.failure(-1, "Snippet code cannot be null or empty", "");
        }

        String preparedSource = prepareSource(code, dependencies);

        Path tempDir = Files.createTempDirectory("jbang_instant_");
        Path tempFile = tempDir.resolve("InstantScript.java");
        try {
            Files.writeString(tempFile, preparedSource, java.nio.charset.StandardCharsets.UTF_8);
            return runScript(tempFile.toAbsolutePath().toString(), scriptArgs, jbangOptions, timeoutSec, workingDir);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception ignore) {}
        }
    }

    public Result runScript(
            String scriptTarget,
            List<String> scriptArgs,
            List<String> jbangOptions,
            int timeoutSec,
            Path workingDir
    ) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(JBangBootstrap.buildCommandPrefix(executable));
        cmd.add("run");

        if (jbangOptions != null) {
            cmd.addAll(jbangOptions);
        }

        cmd.add(scriptTarget);

        if (scriptArgs != null) {
            cmd.addAll(scriptArgs);
        }

        return executeProcess(cmd, timeoutSec, workingDir);
    }

    public Result executeSubcommand(List<String> args, int timeoutSec, Path workingDir)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(JBangBootstrap.buildCommandPrefix(executable));
        if (args != null) {
            cmd.addAll(args);
        }
        return executeProcess(cmd, timeoutSec, workingDir);
    }

    private static final Pattern CLASS_OR_RECORD_PATTERN =
            Pattern.compile("(public\\s+)?(class|record|interface|enum)\\s+\\w+");

    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile("void\\s+main\\s*\\(");

    static String prepareSource(String code, List<String> dependencies) {
        StringBuilder sb = new StringBuilder();

        if (dependencies != null && !dependencies.isEmpty()) {
            for (String dep : dependencies) {
                if (dep != null && !dep.isBlank() && !code.contains(dep.trim())) {
                    sb.append("//DEPS ").append(dep.trim()).append("\n");
                }
            }
        }

        boolean hasClass = CLASS_OR_RECORD_PATTERN.matcher(code).find();
        boolean hasMain  = MAIN_METHOD_PATTERN.matcher(code).find();

        if (hasClass || hasMain) {
            sb.append(code);
        } else {
            sb.append("//JAVA 21+\n");
            sb.append("class InstantScript {\n");
            sb.append("    public static void main(String... args) throws Exception {\n");
            for (String line : code.split("\n")) {
                sb.append("        ").append(line).append("\n");
            }
            sb.append("    }\n");
            sb.append("}\n");
        }

        return sb.toString();
    }

    /**
     * Executes the given command list through {@link LocalProcessSandbox}.
     *
     * <p>The sandbox provides path containment, virtual-thread I/O capture,
     * and timeout management — replacing the previous manual {@code ProcessBuilder}
     * + virtual thread stream reading implementation.</p>
     */
    private Result executeProcess(List<String> cmd, int timeoutSec, Path workingDir)
            throws IOException, InterruptedException {

        // Build the shell command string for the sandbox
        String shellCmd = buildShellCommand(cmd);

        // Configure sandbox — use provided workingDir, or fall back to current dir
        SandboxConfiguration config = new SandboxConfiguration();
        if (workingDir != null) {
            config.setWorkingDirectory(workingDir.toAbsolutePath().toString());
        }
        // Pass through current process environment so JBang can find JAVA_HOME, PATH, etc.
        Map<String, String> env = new HashMap<>(System.getenv());
        config.setEnvironmentVariables(env);

        LocalProcessSandbox sandbox = new LocalProcessSandbox(config);
        try {
            sandbox.start();
            long timeoutMs = timeoutSec > 0 ? (long) timeoutSec * 1000 : 60_000L;
            SandboxExecutionResult result = sandbox.executeCommand(shellCmd, timeoutMs);

            boolean success = result.exitCode() == 0;
            String stdout = result.stdout() != null ? result.stdout().trim() : "";
            String stderr = result.stderr() != null ? result.stderr().trim() : "";

            if (result.exitCode() == 137) {
                // 137 = timeout kill signal used by LocalProcessSandbox
                return Result.failure(137, stdout, "Execution timed out after " + timeoutSec + "s\n" + stderr);
            }

            return new Result(success, result.exitCode(), stdout, stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Sandbox execution failed: " + e.getMessage(), e);
        } finally {
            try {
                sandbox.stop();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Converts a command list into a quoted shell string safe for {@code sh -c} or {@code cmd /c} execution.
     */
    private static String buildShellCommand(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0) sb.append(' ');
            String arg = cmd.get(i);
            // Wrap in quotes if the arg contains spaces
            if (arg.contains(" ") || arg.contains("\t")) {
                sb.append('"').append(arg.replace("\"", "\\\"")).append('"');
            } else {
                sb.append(arg);
            }
        }
        return sb.toString();
    }

    public record Result(boolean success, int exitCode, String stdout, String stderr) {
        public static Result failure(int exitCode, String stdout, String stderr) {
            return new Result(false, exitCode, stdout, stderr);
        }

        public String combinedOutput() {
            if (stdout == null || stdout.isBlank()) return stderr != null ? stderr : "";
            if (stderr == null || stderr.isBlank()) return stdout;
            return stdout + "\n" + stderr;
        }
    }
}
