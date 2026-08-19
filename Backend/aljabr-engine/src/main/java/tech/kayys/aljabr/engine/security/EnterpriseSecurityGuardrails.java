package tech.kayys.aljabr.engine.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enterprise Tool & File Execution Guardrails.
 * Enforces zero-trust policy rules on shell commands, workspace file mutations,
 * and external network interactions.
 */
@ApplicationScoped
public class EnterpriseSecurityGuardrails {

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public record PolicyEvaluation(
        boolean isAllowed,
        RiskLevel riskLevel,
        String ruleViolationMessage,
        boolean requiresHumanApproval
    ) {
        public static PolicyEvaluation allow(RiskLevel risk, boolean requiresApproval) {
            return new PolicyEvaluation(true, risk, null, requiresApproval);
        }

        public static PolicyEvaluation block(String reason) {
            return new PolicyEvaluation(false, RiskLevel.CRITICAL, reason, false);
        }
    }

    private static final Set<String> BLOCKED_PATH_PREFIXES = Set.of(
        "/etc",
        "/var/run",
        "/root",
        "/System",
        "/Library",
        "/proc",
        "/sys",
        "/dev"
    );

    private static final Set<String> SENSITIVE_FILENAME_PATTERNS = Set.of(
        ".env",
        "id_rsa",
        "id_ed25519",
        "credentials",
        ".aws/credentials",
        ".ssh/authorized_keys"
    );

    private static final List<Pattern> DESTRUCTIVE_COMMAND_PATTERNS = List.of(
        Pattern.compile("(?i)\\brm\\s+-[rf]{1,2}\\s+[/~]"),
        Pattern.compile("(?i)\\bmkfs\\b"),
        Pattern.compile("(?i)\\bdd\\s+if=/dev/(?:zero|urandom)\\s+of="),
        Pattern.compile("(?i)\\bchmod\\s+-R\\s+777\\s+/"),
        Pattern.compile("(?i):\\(\\)\\{\\s*:\\|:&\\s*\\};:"), // fork bomb
        Pattern.compile("(?i)(?:nc|ncat|netcat|bash\\s+-i|sh\\s+-i)\\s+.*[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}") // reverse shell
    );

    /**
     * Evaluates whether a proposed shell command violates security policies.
     */
    public PolicyEvaluation evaluateShellCommand(String command, String workingDirectory) {
        if (command == null || command.isBlank()) {
            return PolicyEvaluation.allow(RiskLevel.LOW, false);
        }

        final String trimmed = command.trim();

        // 1. Check against critical destructive command patterns
        for (Pattern pattern : DESTRUCTIVE_COMMAND_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return PolicyEvaluation.block("Execution blocked by enterprise guardrails: Destructive command pattern detected.");
            }
        }

        // 2. High-risk commands requiring explicit human-in-the-loop approval
        if (trimmed.startsWith("git push") ||
            trimmed.contains("git reset --hard") ||
            trimmed.contains("docker run --privileged") ||
            trimmed.contains("sudo ") ||
            trimmed.contains("npm publish") ||
            trimmed.contains("mvn deploy")) {
            return PolicyEvaluation.allow(RiskLevel.HIGH, true);
        }

        // 3. Medium-risk commands (compilations, builds, test runs)
        if (trimmed.startsWith("mvn ") ||
            trimmed.startsWith("gradle ") ||
            trimmed.startsWith("./gradlew ") ||
            trimmed.startsWith("cargo ") ||
            trimmed.startsWith("npm ") ||
            trimmed.startsWith("flutter ")) {
            return PolicyEvaluation.allow(RiskLevel.MEDIUM, false);
        }

        return PolicyEvaluation.allow(RiskLevel.LOW, false);
    }

    /**
     * Evaluates whether a proposed file mutation is within allowed workspace boundaries.
     */
    public PolicyEvaluation evaluateFileAccess(String targetPath, String workspaceRoot) {
        if (targetPath == null || targetPath.isBlank()) {
            return PolicyEvaluation.block("Target path cannot be empty.");
        }

        try {
            Path target = Paths.get(targetPath).normalize().toAbsolutePath();

            // 1. Check blocked system directories
            for (String blockedPrefix : BLOCKED_PATH_PREFIXES) {
                if (target.toString().startsWith(blockedPrefix)) {
                    return PolicyEvaluation.block("Access to protected system path '" + target + "' is prohibited.");
                }
            }

            // 2. Check sensitive credentials files
            for (String sensitive : SENSITIVE_FILENAME_PATTERNS) {
                if (target.toString().endsWith(sensitive)) {
                    return PolicyEvaluation.allow(RiskLevel.HIGH, true);
                }
            }

            // 3. Workspace boundary check (if workspace root specified)
            if (workspaceRoot != null && !workspaceRoot.isBlank()) {
                Path root = Paths.get(workspaceRoot).normalize().toAbsolutePath();
                if (!target.startsWith(root)) {
                    return PolicyEvaluation.allow(RiskLevel.MEDIUM, true);
                }
            }

            return PolicyEvaluation.allow(RiskLevel.LOW, false);
        } catch (Exception e) {
            return PolicyEvaluation.block("Invalid file path format: " + e.getMessage());
        }
    }
}
