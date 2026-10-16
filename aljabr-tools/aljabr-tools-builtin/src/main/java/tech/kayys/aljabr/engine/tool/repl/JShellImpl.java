package tech.kayys.aljabr.engine.tool.repl;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.EvalException;
import jdk.jshell.JShellException;
import jdk.jshell.SourceCodeAnalysis;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Core JShell execution engine for Aljabr.
 */
public class JShellImpl implements AutoCloseable {

    private final ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
    private final PrintStream stdoutStream;
    private final JShell shell;

    public JShellImpl() {
        this.stdoutStream = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
        this.shell = JShell.builder()
                .out(stdoutStream)
                .err(stdoutStream)
                .build();
    }

    public Result evaluate(String code) {
        if (code == null || code.isBlank()) {
            return Result.failure("<empty snippet>");
        }

        stdoutBytes.reset();
        List<String> snippetResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        SourceCodeAnalysis sca = shell.sourceCodeAnalysis();
        String remaining = code;

        while (remaining != null && !remaining.isBlank()) {
            SourceCodeAnalysis.CompletionInfo info = sca.analyzeCompletion(remaining);
            String snippetSource = info.source();
            if (snippetSource == null || snippetSource.isBlank()) {
                break;
            }

            for (SnippetEvent event : shell.eval(snippetSource)) {
                Snippet snippet = event.snippet();

                shell.diagnostics(snippet).forEach(d -> {
                    if (d.isError()) {
                        errors.add("[compile] " + d.getMessage(null));
                    }
                });

                if (event.status() == Snippet.Status.REJECTED) {
                    errors.add("[rejected] " + snippetSource(snippet));
                    continue;
                }

                if (event.exception() != null) {
                    errors.add(formatException(event.exception()));
                    continue;
                }

                if (event.value() != null && !event.value().isEmpty()) {
                    snippetResults.add(event.value());
                }
            }

            remaining = info.remaining();
        }

        stdoutStream.flush();
        String stdout = stdoutBytes.toString(StandardCharsets.UTF_8).trim();
        String results = String.join("\n", snippetResults).trim();

        if (!errors.isEmpty()) {
            return Result.failure(String.join("\n", errors), stdout);
        }

        String finalOutput;
        if (!results.isEmpty() && !stdout.isEmpty()) {
            finalOutput = stdout + "\n" + results;
        } else if (!results.isEmpty()) {
            finalOutput = results;
        } else if (!stdout.isEmpty()) {
            finalOutput = stdout;
        } else {
            finalOutput = "(no output)";
        }

        return Result.success(finalOutput, stdout.isEmpty() ? null : stdout);
    }

    public List<String> activeImports() {
        return shell.imports().map(i -> i.source().trim()).toList();
    }

    public List<String> activeVariables() {
        return shell.variables().map(v -> v.typeName() + " " + v.name() + " = " + shell.varValue(v)).toList();
    }

    @Override
    public void close() {
        shell.close();
    }

    public record Result(boolean success, String output, String stdout, String error) {
        public static Result success(String output) { return new Result(true, output, null, null); }
        public static Result success(String output, String stdout) { return new Result(true, output, stdout, null); }
        public static Result failure(String error) { return new Result(false, null, null, error); }
        public static Result failure(String error, String stdout) { return new Result(false, null, stdout, error); }
    }

    private String snippetSource(Snippet s) {
        String src = s.source();
        return src.length() > 120 ? src.substring(0, 120) + "…" : src;
    }

    private String formatException(JShellException ex) {
        if (ex instanceof EvalException ee) {
            return "[runtime] " + ee.getExceptionClassName() + ": " + ee.getMessage();
        }
        return "[exception] " + ex.getMessage();
    }
}
