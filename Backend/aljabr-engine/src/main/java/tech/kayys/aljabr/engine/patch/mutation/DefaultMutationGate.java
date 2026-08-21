package tech.kayys.aljabr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.aljabr.api.agent.AljabrState;
import tech.kayys.aljabr.engine.patch.ChangeRisk;
import tech.kayys.aljabr.engine.patch.FilePatch;
import tech.kayys.aljabr.engine.patch.PatchHunk;
import tech.kayys.aljabr.engine.patch.PatchProposal;
import tech.kayys.aljabr.engine.patch.RiskLevel;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-owned mutation authorization.
 *
 * <p>This gate deliberately evaluates the concrete proposal rather than
 * trusting a model supplied risk label. It enforces path boundaries, mutation
 * budgets, lifecycle state and critical-risk approval.</p>
 */
@ApplicationScoped
public class DefaultMutationGate implements MutationGate {

    @Override
    public Uni<MutationAuthorization> authorize(
            PatchProposal proposal,
            ChangeRisk risk,
            AljabrState state,
            MutationPolicy policy) {

        return Uni.createFrom().item(() -> evaluate(
                proposal,
                risk,
                state,
                policy != null ? policy : MutationPolicy.defaultPolicy()));
    }

    private MutationAuthorization evaluate(
            PatchProposal proposal,
            ChangeRisk risk,
            AljabrState state,
            MutationPolicy policy) {

        if (proposal == null || proposal.patches().isEmpty()) {
            return MutationAuthorization.deny("Mutation proposal is empty");
        }

        if (state != null && !mutationPhase(state.phase())) {
            return MutationAuthorization.deny(
                    "Mutations are not permitted during agent phase " + state.phase());
        }

        List<String> violations = new ArrayList<>();
        int addedLines = 0;
        int deletedLines = 0;

        if (proposal.patches().size() > policy.maxFiles()) {
            violations.add("File mutation budget exceeded: "
                    + proposal.patches().size() + " > " + policy.maxFiles());
        }

        for (FilePatch patch : proposal.patches()) {
            String path = normalize(patch.path());

            if (matchesAny(path, policy.deniedPaths())) {
                violations.add("Denied mutation path: " + path);
                continue;
            }

            if (!matchesAny(path, policy.allowedPaths())) {
                violations.add("Path outside mutation allowlist: " + path);
            }

            for (PatchHunk hunk : patch.hunks()) {
                int oldLines = lineCount(hunk.expectedOldText());
                int newLines = lineCount(hunk.replacementText());
                deletedLines += oldLines;
                addedLines += newLines;
            }
        }

        if (addedLines > policy.maxAddedLines()) {
            violations.add("Added-line budget exceeded: "
                    + addedLines + " > " + policy.maxAddedLines());
        }
        if (deletedLines > policy.maxDeletedLines()) {
            violations.add("Deleted-line budget exceeded: "
                    + deletedLines + " > " + policy.maxDeletedLines());
        }

        if (!violations.isEmpty()) {
            return MutationAuthorization.deny(String.join("; ", violations));
        }

        RiskLevel level = risk != null && risk.level() != null
                ? risk.level()
                : RiskLevel.HIGH;

        if (level == RiskLevel.CRITICAL && policy.requireUserForCriticalRisk()) {
            return MutationAuthorization.requireUser(
                    "Critical-risk mutation requires user approval"
                            + riskReasonSuffix(risk));
        }

        if (level == RiskLevel.HIGH) {
            return MutationAuthorization.requireUser(
                    "High-risk mutation requires explicit approval"
                            + riskReasonSuffix(risk));
        }

        return MutationAuthorization.allow();
    }

    private boolean mutationPhase(AgentPhase phase) {
        return phase == null
                || phase == AgentPhase.EXECUTING
                || phase == AgentPhase.REPLANNING
                || phase == AgentPhase.RECOVERING;
    }

    private String riskReasonSuffix(ChangeRisk risk) {
        if (risk == null || risk.reasons().isEmpty()) {
            return "";
        }
        return ": " + String.join(", ", risk.reasons());
    }

    private int lineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\R", -1).length;
    }

    private String normalize(Path path) {
        if (path == null) return "";
        return path.normalize().toString().replace('\\', '/');
    }

    private boolean matchesAny(String path, java.util.Set<String> patterns) {
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            PathMatcher matcher = FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern);
            if (matcher.matches(Path.of(path))) return true;
        }
        return false;
    }
}
