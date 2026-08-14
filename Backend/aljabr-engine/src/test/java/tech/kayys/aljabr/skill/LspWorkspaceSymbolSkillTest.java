package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.document.Range;
import tech.kayys.muqabalah.document.Position;
import tech.kayys.muqabalah.language.LanguageTypes.SymbolEntry;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LspWorkspaceSymbolSkillTest {

    private LspWorkspaceSymbolSkill skill;
    private CodeTool mockCodeTool;

    @BeforeEach
    void setUp() {
        mockCodeTool = mock(CodeTool.class);
        skill = new LspWorkspaceSymbolSkill();
        skill.codeTool = mockCodeTool;
    }

    @Test
    void testWorkspaceSymbolSuccess() {
        SymbolEntry entry = new SymbolEntry("TestClass", "Class", URI.create("file:///test.java"),
                new Range(new Position(0, 0), new Position(1, 0)), null, List.of());
        when(mockCodeTool.workspaceSymbols(anyString(), anyString()))
                .thenReturn(Uni.createFrom().item(List.of(entry)));

        Map<String, Object> context = Map.of(
                "workspace_path", "/ws",
                "query", "Test");

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));
        List<Map<String, Object>> symbols = (List<Map<String, Object>>) output.get("symbols");
        assertEquals(1, symbols.size());
        assertEquals("TestClass", symbols.get(0).get("name"));
    }
}
