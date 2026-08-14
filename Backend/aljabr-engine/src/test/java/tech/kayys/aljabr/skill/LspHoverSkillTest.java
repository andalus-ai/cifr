package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.language.LanguageTypes.HoverResult;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LspHoverSkillTest {

    private LspHoverSkill skill;
    private CodeTool mockCodeTool;

    @BeforeEach
    void setUp() {
        mockCodeTool = mock(CodeTool.class);
        skill = new LspHoverSkill();
        skill.codeTool = mockCodeTool;
    }

    @Test
    void testHoverSuccess() {
        HoverResult result = new HoverResult("hover docs", null);
        when(mockCodeTool.hover(anyString(), any(Path.class), anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(result));

        Map<String, Object> context = Map.of(
                "workspace_path", "/ws",
                "file_path", "/ws/test.java",
                "line", 10,
                "character", 5);

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
        assertEquals("hover docs", output.get("content"));
    }

    @Test
    void testHoverEmptyContent() {
        HoverResult result = new HoverResult(null, null);
        when(mockCodeTool.hover(anyString(), any(Path.class), anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(result));

        Map<String, Object> context = Map.of(
                "workspace_path", "/ws",
                "file_path", "/ws/test.java",
                "line", 10,
                "character", 5);

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
        assertEquals("No hover information found at this position.", output.get("content"));
    }
}
