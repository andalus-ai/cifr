package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.document.Position;
import tech.kayys.muqabalah.document.Range;
import tech.kayys.muqabalah.language.LanguageTypes.CodeLocation;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LspQuerySkillTest {

        private LspQuerySkill skill;
        private CodeTool mockCodeTool;

        @BeforeEach
        void setUp() {
                mockCodeTool = mock(CodeTool.class);
                skill = new LspQuerySkill();
                skill.codeTool = mockCodeTool;
        }

        @Test
        void testDefinitionQuery() throws Exception {
                CodeLocation loc = new CodeLocation(
                                new URI("file:///ws/test.java"),
                                new Range(new Position(5, 0), new Position(5, 10)));
                when(mockCodeTool.definition(anyString(), any(Path.class), anyInt(), anyInt()))
                                .thenReturn(Uni.createFrom().item(List.of(loc)));

                Map<String, Object> context = Map.of(
                                "workspace_path", "/ws",
                                "file_path", "/ws/test.java",
                                "line", 10,
                                "character", 5,
                                "query_type", "definition");

                Map<String, Object> output = skill.execute(context).await().indefinitely();

                assertEquals("success", output.get("status"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
                assertEquals(1, results.size());
                assertEquals("file:///ws/test.java", results.get(0).get("uri"));
                assertEquals(5, results.get(0).get("start_line"));
        }

        @Test
        void testReferencesQuery() throws Exception {
                CodeLocation loc = new CodeLocation(
                                new URI("file:///ws/test.java"),
                                new Range(new Position(8, 0), new Position(8, 10)));
                when(mockCodeTool.references(anyString(), any(Path.class), anyInt(), anyInt()))
                                .thenReturn(Uni.createFrom().item(List.of(loc)));

                Map<String, Object> context = Map.of(
                                "workspace_path", "/ws",
                                "file_path", "/ws/test.java",
                                "line", 10,
                                "character", 5,
                                "query_type", "references");

                Map<String, Object> output = skill.execute(context).await().indefinitely();

                assertEquals("success", output.get("status"));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");
                assertEquals(1, results.size());
                assertEquals("file:///ws/test.java", results.get(0).get("uri"));
                assertEquals(8, results.get(0).get("start_line"));
        }
}
