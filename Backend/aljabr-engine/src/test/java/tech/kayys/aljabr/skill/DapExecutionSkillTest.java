package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.DebugTool;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DapExecutionSkillTest {

    private DapExecutionSkill skill;
    private DebugTool mockDebugTool;

    @BeforeEach
    void setUp() {
        mockDebugTool = mock(DebugTool.class);
        skill = new DapExecutionSkill();
        skill.debugTool = mockDebugTool;
    }

    @Test
    void testResumeSuccess() {
        when(mockDebugTool.resume(anyString(), anyInt()))
                .thenReturn(Uni.createFrom().item((Void) null));

        Map<String, Object> context = Map.of(
                "session_id", "session-123",
                "thread_id", 1,
                "action", "resume");

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
    }
}
