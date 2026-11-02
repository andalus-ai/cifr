package tech.kayys.cifr.engine.patch.mutation;

import org.junit.jupiter.api.Test;
import tech.kayys.cifr.api.agent.AgentPhase;
import tech.kayys.cifr.api.agent.CifrState;
import tech.kayys.cifr.engine.patch.ChangePlanId;
import tech.kayys.cifr.engine.patch.ChangeRisk;
import tech.kayys.cifr.engine.patch.FilePatch;
import tech.kayys.cifr.engine.patch.PatchHunk;
import tech.kayys.cifr.engine.patch.PatchProposal;
import tech.kayys.cifr.engine.patch.RiskLevel;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMutationGateTest {

    private final DefaultMutationGate gate = new DefaultMutationGate();

    @Test
    void allowsSmallLowRiskSourceMutationDuringExecution() {
        MutationAuthorization result = gate.authorize(
                proposal("src/Foo.java", "old", "new"),
                new ChangeRisk(RiskLevel.LOW, List.of("localized")),
                state(AgentPhase.EXECUTING),
                MutationPolicy.defaultPolicy())
                .await().indefinitely();

        assertEquals(MutationAuthorization.Status.ALLOW, result.status());
    }

    @Test
    void deniesPathOutsideAllowlist() {
        MutationAuthorization result = gate.authorize(
                proposal("config/application.properties", "old", "new"),
                new ChangeRisk(RiskLevel.LOW, List.of()),
                state(AgentPhase.EXECUTING),
                MutationPolicy.defaultPolicy())
                .await().indefinitely();

        assertEquals(MutationAuthorization.Status.DENY, result.status());
    }

    @Test
    void criticalRiskRequiresUser() {
        MutationAuthorization result = gate.authorize(
                proposal("src/Foo.java", "old", "new"),
                new ChangeRisk(RiskLevel.CRITICAL, List.of("public API")),
                state(AgentPhase.EXECUTING),
                MutationPolicy.defaultPolicy())
                .await().indefinitely();

        assertEquals(MutationAuthorization.Status.REQUIRE_USER, result.status());
    }

    @Test
    void highRiskAlsoRequiresExplicitApproval() {
        MutationAuthorization result = gate.authorize(
                proposal("src/Foo.java", "old", "new"),
                new ChangeRisk(RiskLevel.HIGH, List.of("large fan-out")),
                state(AgentPhase.EXECUTING),
                MutationPolicy.defaultPolicy())
                .await().indefinitely();

        assertEquals(MutationAuthorization.Status.REQUIRE_USER, result.status());
    }

    @Test
    void deniesMutationOutsideExecutionLifecycle() {
        MutationAuthorization result = gate.authorize(
                proposal("src/Foo.java", "old", "new"),
                new ChangeRisk(RiskLevel.LOW, List.of()),
                state(AgentPhase.OBSERVING),
                MutationPolicy.defaultPolicy())
                .await().indefinitely();

        assertEquals(MutationAuthorization.Status.DENY, result.status());
    }

    @Test
    void enforcesLineBudget() {
        MutationPolicy policy = new MutationPolicy(
                java.util.Set.of("src/**"),
                java.util.Set.of(),
                10,
                2,
                2,
                true);

        MutationAuthorization result = gate.authorize(
                proposal("src/Foo.java", "old", "a\nb\nc"),
                new ChangeRisk(RiskLevel.LOW, List.of()),
                state(AgentPhase.EXECUTING),
                policy)
                .await().indefinitely();

        assertEquals(MutationAuthorization.Status.DENY, result.status());
    }

    private PatchProposal proposal(String path, String oldText, String newText) {
        return new PatchProposal(
                ChangePlanId.generate(),
                List.of(new FilePatch(
                        Path.of(path),
                        null,
                        List.of(new PatchHunk(1, 1, oldText, newText)))),
                List.of("evidence-1"));
    }

    private CifrState state(AgentPhase phase) {
        return new CifrState("exec-1", phase, null, null, null, null);
    }
}
