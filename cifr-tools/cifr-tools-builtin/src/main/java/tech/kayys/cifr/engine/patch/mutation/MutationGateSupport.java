package tech.kayys.cifr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.api.agent.CifrState;
import tech.kayys.cifr.engine.patch.ChangePlanId;
import tech.kayys.cifr.engine.patch.ChangeRisk;
import tech.kayys.cifr.engine.patch.FilePatch;
import tech.kayys.cifr.engine.patch.PatchHunk;
import tech.kayys.cifr.engine.patch.PatchProposal;
import tech.kayys.cifr.engine.patch.RiskLevel;

import java.nio.file.Path;
import java.util.List;

/**
 * Shared authorization adapter for all builtin mutation entry points.
 * Risk is conservatively derived from the concrete edit, never accepted from
 * the caller. Unknown or broad changes require explicit approval.
 */
public final class MutationGateSupport {

    private MutationGateSupport() {}

    public static Uni<MutationAuthorization> authorize(MutationGate gate, EditProposal proposal) {
        if (gate == null) {
            return Uni.createFrom().item(() ->
                    MutationAuthorization.deny("Mutation gate is not configured"));
        }
        if (proposal == null || proposal.edits().isEmpty()) {
            return Uni.createFrom().item(() ->
                    MutationAuthorization.deny("Mutation proposal is empty"));
        }

        List<FilePatch> patches = proposal.edits().stream()
                .map(file -> new FilePatch(
                        file.file(),
                        null,
                        file.edits().stream()
                                .map(edit -> new PatchHunk(
                                        edit.startLine(),
                                        edit.endLine(),
                                        edit.expectedText(),
                                        edit.replacementText()))
                                .toList()))
                .toList();

        boolean boundedLowRisk = patches.size() <= 3
                && patches.stream().allMatch(p -> isLowRiskPath(p.path()))
                && patches.stream().flatMap(p -> p.hunks().stream())
                    .mapToInt(h -> lineCount(h.expectedOldText()) + lineCount(h.replacementText()))
                    .sum() <= 100;

        ChangeRisk risk = new ChangeRisk(
                boundedLowRisk ? RiskLevel.LOW : RiskLevel.HIGH,
                boundedLowRisk
                        ? List.of("bounded source/documentation edit")
                        : List.of("broad or sensitive mutation requires approval"));

        PatchProposal gateProposal = new PatchProposal(
                ChangePlanId.generate(), patches, proposal.supportingEvidence());

        return gate.authorize(gateProposal, risk, (CifrState) null, MutationPolicy.defaultPolicy());
    }

    private static boolean isLowRiskPath(Path path) {
        if (path == null || path.isAbsolute()) return false;
        String normalized = path.normalize().toString().replace('\\', '/');
        return normalized.startsWith("src/")
                || normalized.startsWith("test/")
                || normalized.startsWith("docs/");
    }

    private static int lineCount(String value) {
        return value == null || value.isEmpty() ? 0 : value.split("\\R", -1).length;
    }
}
