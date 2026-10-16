package tech.kayys.aljabr.engine.verify;

import java.util.Optional;

/**
 * Represents a diagnostic message from tool execution (error, warning, info).
 */
public record Diagnostic(
        String severity, // "error", "warning", "info", "hint"
        String message,
        String file,
        Integer line,
        Integer column,
        String code) {
    public Diagnostic(String severity, String message, String file, Integer line, Integer column, String code) {
        this.severity = severity;
        this.message = message;
        this.file = file;
        this.line = line;
        this.column = column;
        this.code = code;
    }

    public boolean isError() {
        return "error".equalsIgnoreCase(severity);
    }

    public boolean isWarning() {
        return "warning".equalsIgnoreCase(severity);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (file != null) {
            sb.append(file);
            if (line != null) {
                sb.append(":").append(line);
                if (column != null) {
                    sb.append(":").append(column);
                }
            }
            sb.append(": ");
        }
        sb.append(severity).append(": ").append(message);
        if (code != null) {
            sb.append(" [").append(code).append("]");
        }
        return sb.toString();
    }

    public static Diagnostic error(String message, String file, Integer line) {
        return new Diagnostic("error", message, file, line, null, null);
    }

    public static Diagnostic warning(String message, String file, Integer line) {
        return new Diagnostic("warning", message, file, line, null, null);
    }

    public static Diagnostic info(String message) {
        return new Diagnostic("info", message, null, null, null, null);
    }
}