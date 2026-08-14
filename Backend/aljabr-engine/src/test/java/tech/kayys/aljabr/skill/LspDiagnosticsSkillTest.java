package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.CodeTool;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LspDiagnosticsSkillTest {

    private LspDiagnosticsSkill skill;
    private CodeTool mockCodeTool;

    @BeforeEach
    void setUp() {
        mockCodeTool = mock(CodeTool.class);
        skill = new LspDiagnosticsSkill();
        skill.codeTool = mockCodeTool;
    }

    @Test
    void testFixDiagnosticsSuccess() {
        when(mockCodeTool.fixDiagnostics(anyString(), any(Path.class)))
                .thenReturn(Uni.createFrom().item((Void) null));

        Map<String, Object> context = Map.of(
                "workspace_path", "/ws",
                "file_path", "/ws/test.java");

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
    }
}
