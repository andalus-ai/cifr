package tech.kayys.cifr.engine.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EnterpriseSecurityGuardrailsTest {

    private final EnterpriseSecurityGuardrails guardrails = new EnterpriseSecurityGuardrails();

    @Test
    public void testBlockDestructiveRmRfRoot() {
        EnterpriseSecurityGuardrails.PolicyEvaluation eval = guardrails.evaluateShellCommand("rm -rf /", "/tmp");
        assertFalse(eval.isAllowed());
        assertEquals(EnterpriseSecurityGuardrails.RiskLevel.CRITICAL, eval.riskLevel());
        assertNotNull(eval.ruleViolationMessage());
    }

    @Test
    public void testBlockForkBomb() {
        EnterpriseSecurityGuardrails.PolicyEvaluation eval = guardrails.evaluateShellCommand(":(){ :|:& };:", "/tmp");
        assertFalse(eval.isAllowed());
        assertEquals(EnterpriseSecurityGuardrails.RiskLevel.CRITICAL, eval.riskLevel());
    }

    @Test
    public void testEscalateHighRiskGitPush() {
        EnterpriseSecurityGuardrails.PolicyEvaluation eval = guardrails.evaluateShellCommand("git push origin main", "/project");
        assertTrue(eval.isAllowed());
        assertEquals(EnterpriseSecurityGuardrails.RiskLevel.HIGH, eval.riskLevel());
        assertTrue(eval.requiresHumanApproval());
    }

    @Test
    public void testAllowBuildCommands() {
        EnterpriseSecurityGuardrails.PolicyEvaluation eval = guardrails.evaluateShellCommand("mvn clean test", "/project");
        assertTrue(eval.isAllowed());
        assertEquals(EnterpriseSecurityGuardrails.RiskLevel.MEDIUM, eval.riskLevel());
        assertFalse(eval.requiresHumanApproval());
    }

    @Test
    public void testBlockSystemPathAccess() {
        EnterpriseSecurityGuardrails.PolicyEvaluation eval = guardrails.evaluateFileAccess("/etc/shadow", "/home/user/project");
        assertFalse(eval.isAllowed());
        assertEquals(EnterpriseSecurityGuardrails.RiskLevel.CRITICAL, eval.riskLevel());
    }
}
