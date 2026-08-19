package tech.kayys.aljabr.engine.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SecretScrubberTest {

    private final SecretScrubber scrubber = new SecretScrubber();

    @Test
    public void testScrubOpenAiKey() {
        String input = "Please connect using sk-proj-1234567890abcdef1234567890abcdef to OpenAI";
        SecretScrubber.ScrubResult result = scrubber.scrub(input);

        assertNotEquals(input, result.sanitizedText());
        assertTrue(result.sanitizedText().contains("[REDACTED:API_KEY_"));
        assertEquals(1, result.redactionsCount());
    }

    @Test
    public void testScrubGithubToken() {
        String input = "git push with token ghp_123456789012345678901234567890123456";
        SecretScrubber.ScrubResult result = scrubber.scrub(input);

        assertNotEquals(input, result.sanitizedText());
        assertTrue(result.sanitizedText().contains("[REDACTED:GITHUB_TOKEN_"));
        assertEquals(1, result.redactionsCount());
    }

    @Test
    public void testScrubDbPassword() {
        String input = "Database URL is postgres://admin:SuperSecretPass123@db.prod.internal:5432/main";
        SecretScrubber.ScrubResult result = scrubber.scrub(input);

        assertFalse(result.sanitizedText().contains("SuperSecretPass123"));
        assertTrue(result.sanitizedText().contains(":[REDACTED_PASSWORD]@"));
        assertEquals(1, result.redactionsCount());
    }

    @Test
    public void testScrubPrivateKey() {
        String input = "Here is the key:\n-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0\n-----END RSA PRIVATE KEY-----\nUse it carefully.";
        SecretScrubber.ScrubResult result = scrubber.scrub(input);

        assertFalse(result.sanitizedText().contains("MIIEowIBAAKCAQEA0"));
        assertTrue(result.sanitizedText().contains("[REDACTED:PRIVATE_KEY]"));
        assertEquals(1, result.redactionsCount());
    }

    @Test
    public void testCleanTextUnmodified() {
        String clean = "This is a normal code snippet with no keys: function add(a, b) { return a + b; }";
        SecretScrubber.ScrubResult result = scrubber.scrub(clean);

        assertEquals(clean, result.sanitizedText());
        assertEquals(0, result.redactionsCount());
    }
}
