package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.DebugTool;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DapSessionSkillTest {

    private DapSessionSkill skill;
    private DebugTool mockDebugTool;

    @BeforeEach
    void setUp() {
        mockDebugTool = mock(DebugTool.class);
        skill = new DapSessionSkill();
        skill.debugTool = mockDebugTool;
    }

    @Test
    void testLaunchSuccess() {
        when(mockDebugTool.launch(anyString(), any(Path.class), any(Map.class)))
                .thenReturn(Uni.createFrom().item("session-123"));

        Map<String, Object> context = Map.of(
                "workspace_path", "/ws",
                "program", "/ws/main.py",
                "action", "launch");

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
        assertEquals("session-123", output.get("session_id"));
    }
}
