package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.DebugTool;
import tech.kayys.muqabalah.api.DebugTool.StackFrame;
import tech.kayys.muqabalah.api.DebugTool.StackTrace;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DapInspectionSkillTest {

    private DapInspectionSkill skill;
    private DebugTool mockDebugTool;

    @BeforeEach
    void setUp() {
        mockDebugTool = mock(DebugTool.class);
        skill = new DapInspectionSkill();
        skill.debugTool = mockDebugTool;
    }

    @Test
    void testStackTraceSuccess() {
        StackFrame frame = new StackFrame(1, "main", Path.of("/ws/main.py"), 10, 0);
        StackTrace stackTrace = new StackTrace(List.of(frame));

        when(mockDebugTool.stackTrace(anyString(), anyInt()))
                .thenReturn(Uni.createFrom().item(stackTrace));

        Map<String, Object> context = Map.of(
                "session_id", "session-123",
                "thread_id", 1,
                "action", "stacktrace");

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
        List<Map<String, Object>> frames = (List<Map<String, Object>>) output.get("result");
        assertEquals(1, frames.size());
        assertEquals(1, frames.get(0).get("id"));
        assertEquals("main", frames.get(0).get("name"));
    }
}
