package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.DebugTool;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DapBreakpointSkillTest {

    private DapBreakpointSkill skill;
    private DebugTool mockDebugTool;

    @BeforeEach
    void setUp() {
        mockDebugTool = mock(DebugTool.class);
        skill = new DapBreakpointSkill();
        skill.debugTool = mockDebugTool;
    }

    @Test
    void testSetBreakpointsSuccess() {
        when(mockDebugTool.setBreakpoints(anyString(), any(Path.class), anyList()))
                .thenReturn(Uni.createFrom().item((Void) null));

        Map<String, Object> context = Map.of(
                "session_id", "session-123",
                "file_path", "/ws/main.py",
                "lines", List.of(10, 20));

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
    }
}
