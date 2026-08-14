package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.document.Position;
import tech.kayys.muqabalah.document.Range;
import tech.kayys.muqabalah.language.LanguageTypes.SymbolEntry;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LspDocumentSymbolSkillTest {

    private LspDocumentSymbolSkill skill;
    private CodeTool mockCodeTool;

    @BeforeEach
    void setUp() {
        mockCodeTool = mock(CodeTool.class);
        skill = new LspDocumentSymbolSkill();
        skill.codeTool = mockCodeTool;
    }

    @Test
    void testDocumentSymbolQuery() throws Exception {
        SymbolEntry symbol = new SymbolEntry(
                "MyClass",
                "Class",
                new URI("file:///ws/test.java"),
                new Range(new Position(1, 0), new Position(10, 0)),
                "",
                List.of());
        when(mockCodeTool.documentSymbols(anyString(), any(Path.class)))
                .thenReturn(Uni.createFrom().item(List.of(symbol)));

        Map<String, Object> context = Map.of(
                "workspace_path", "/ws",
                "file_path", "/ws/test.java");

        Map<String, Object> output = skill.execute(context).await().indefinitely();

        assertEquals("success", output.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("symbols");
        assertEquals(1, results.size());
        assertEquals("MyClass", results.get(0).get("name"));
        assertEquals("Class", results.get(0).get("kind"));
        assertEquals(1, results.get(0).get("line"));
    }
}
