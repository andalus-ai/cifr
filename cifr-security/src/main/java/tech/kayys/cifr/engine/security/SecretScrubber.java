package tech.kayys.cifr.engine.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise PII & Secret Redaction Engine.
 * Automatically detects and redacts high-risk secrets, API keys, private keys,
 * and sensitive connection strings before sending prompts to LLM providers
 * or storing in audit logs.
 */
@ApplicationScoped
public class SecretScrubber {

    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("(?i)(?:aws_access_key_id|aws_secret_access_key|access_key|secret_key)\\s*[:=]\\s*['\"]?([A-Za-z0-9/+=]{16,40})['\"]?");
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-(?:proj-|live-)?[a-zA-Z0-9_-]{20,}");
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile("(?:ghp|gho|ghu|ghs|ghr|github_pat)_[a-zA-Z0-9_]{36,255}");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+ PRIVATE KEY-----");
    private static final Pattern DB_CONN_STRING_PATTERN = Pattern.compile("(?:postgres|postgresql|mysql|mongodb|redis|amqp)://(?:[^:]+):([^@]+)@");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9-_=]+\\.eyJ[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]+");

    public record ScrubResult(String sanitizedText, int redactionsCount) {}

    /**
     * Sanitizes input text, replacing all detected secrets with cryptographic digest tokens.
     */
    public ScrubResult scrub(String input) {
        if (input == null || input.isEmpty()) {
            return new ScrubResult(input, 0);
        }

        String result = input;
        int count = 0;

        // 1. Private keys
        Matcher pkMatcher = PRIVATE_KEY_PATTERN.matcher(result);
        if (pkMatcher.find()) {
            result = pkMatcher.replaceAll("[REDACTED:PRIVATE_KEY]");
            count++;
        }

        // 2. OpenAI / Anthropic API keys
        Matcher oaiMatcher = OPENAI_KEY_PATTERN.matcher(result);
        while (oaiMatcher.find()) {
            String match = oaiMatcher.group();
            String digest = computeHashPrefix(match);
            result = result.replace(match, "[REDACTED:API_KEY_" + digest + "]");
            count++;
        }

        // 3. GitHub personal access tokens
        Matcher ghMatcher = GITHUB_TOKEN_PATTERN.matcher(result);
        while (ghMatcher.find()) {
            String match = ghMatcher.group();
            String digest = computeHashPrefix(match);
            result = result.replace(match, "[REDACTED:GITHUB_TOKEN_" + digest + "]");
            count++;
        }

        // 4. Database passwords in connection strings
        Matcher dbMatcher = DB_CONN_STRING_PATTERN.matcher(result);
        while (dbMatcher.find()) {
            String password = dbMatcher.group(1);
            result = result.replace(":" + password + "@", ":[REDACTED_PASSWORD]@");
            count++;
        }

        // 5. JWT tokens
        Matcher jwtMatcher = JWT_PATTERN.matcher(result);
        while (jwtMatcher.find()) {
            String match = jwtMatcher.group();
            if (match.length() > 30) {
                String digest = computeHashPrefix(match);
                result = result.replace(match, "[REDACTED:JWT_" + digest + "]");
                count++;
            }
        }

        return new ScrubResult(result, count);
    }

    private String computeHashPrefix(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (Exception e) {
            return "SEC";
        }
    }
}
