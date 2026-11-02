package tech.kayys.cifr.engine.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.lifecycle.AgentTask;
import tech.kayys.cifr.engine.policy.PolicyContext;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskGoal;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ActionAuthorizationProtocolTest {

    private DefaultAuthorizationService authService;
    private PolicyContext context;

    @BeforeEach
    public void setup() {
        authService = new DefaultAuthorizationService();
        AgentTask task = AgentTask.create(TaskId.of("t-auth-1"), "ws-1", TaskGoal.of("Test auth"));
        context = PolicyContext.of(task);
    }

    @Test
    public void testAuthorizeProposal() {
        ActionProposal proposal = new ActionProposal(
            "prop-1",
            TaskId.of("t-auth-1"),
            ActionType.MODIFY,
            ActionTarget.file("src/Main.java"),
            Map.of("diff", "+ public void run() {}"),
            "Implement feature",
            Set.of("ev-1"),
            0.95
        );

        AuthorizationResult result = authService.authorize(proposal, context).await().indefinitely();

        assertTrue(result.isAuthorized());
        assertTrue(result.action().isPresent());

        AuthorizedAction action = result.action().get();
        assertEquals("prop-1", action.proposal().id());
        assertFalse(action.isExpired());
    }

    @Test
    public void testExpiredActionReauthorizationFails() {
        ActionProposal proposal = ActionProposal.of("prop-2", TaskId.of("t-auth-1"), ActionType.READ, ActionTarget.file("README.md"), "Read doc");

        // Create an already expired action
        AuthorizedAction expiredAction = new AuthorizedAction(
            "act-expired",
            proposal,
            Instant.now().minusSeconds(600),
            Instant.now().minusSeconds(300)
        );

        assertTrue(expiredAction.isExpired());

        AuthorizationResult reauthResult = authService.reauthorize(expiredAction, context).await().indefinitely();
        assertFalse(reauthResult.isAuthorized());
        assertEquals("DENY", reauthResult.decision().reason().code());
    }
}
