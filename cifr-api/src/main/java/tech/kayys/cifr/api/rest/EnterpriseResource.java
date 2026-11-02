package tech.kayys.cifr.api.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.cifr.engine.audit.EnterpriseAuditLedger;
import tech.kayys.cifr.engine.router.ResilientModelRouter;
import tech.kayys.cifr.engine.security.EnterpriseSecurityGuardrails;
import tech.kayys.cifr.engine.security.SecretScrubber;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/v1/enterprise")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EnterpriseResource {

    @Inject
    EnterpriseAuditLedger auditLedger;

    @Inject
    EnterpriseSecurityGuardrails securityGuardrails;

    @Inject
    SecretScrubber secretScrubber;

    @Inject
    ResilientModelRouter modelRouter;

    @GET
    @Path("/audit")
    public Response getAuditTrail() {
        List<EnterpriseAuditLedger.AuditEntry> entries = auditLedger.getRecentEntries();
        boolean isTamperFree = auditLedger.verifyLedgerIntegrity();

        Map<String, Object> res = new HashMap<>();
        res.put("entries", entries);
        res.put("totalCount", entries.size());
        res.put("isIntegrityVerified", isTamperFree);
        res.put("complianceStandard", "SOC2-Type2 / ISO27001 / HIPAA");

        return Response.ok(res).build();
    }

    @GET
    @Path("/router/health")
    public Response getRouterHealth() {
        Map<ResilientModelRouter.InferenceTier, ResilientModelRouter.TierHealth> healthMap = modelRouter.getHealthSummary();
        ResilientModelRouter.InferenceTier optimal = modelRouter.resolveOptimalTier();

        Map<String, Object> res = new HashMap<>();
        res.put("optimalTier", optimal.name());
        res.put("optimalDisplayName", optimal.displayName);
        res.put("tiers", healthMap);

        return Response.ok(res).build();
    }

    @POST
    @Path("/security/scrub")
    public Response scrubText(Map<String, String> request) {
        String input = request.getOrDefault("text", "");
        SecretScrubber.ScrubResult result = secretScrubber.scrub(input);

        Map<String, Object> res = new HashMap<>();
        res.put("sanitizedText", result.sanitizedText());
        res.put("redactionsCount", result.redactionsCount());

        return Response.ok(res).build();
    }

    @POST
    @Path("/security/evaluate-command")
    public Response evaluateCommand(Map<String, String> request) {
        String command = request.getOrDefault("command", "");
        String cwd = request.getOrDefault("cwd", "");

        EnterpriseSecurityGuardrails.PolicyEvaluation eval = securityGuardrails.evaluateShellCommand(command, cwd);

        Map<String, Object> res = new HashMap<>();
        res.put("isAllowed", eval.isAllowed());
        res.put("riskLevel", eval.riskLevel().name());
        res.put("ruleViolationMessage", eval.ruleViolationMessage());
        res.put("requiresHumanApproval", eval.requiresHumanApproval());

        return Response.ok(res).build();
    }
}
