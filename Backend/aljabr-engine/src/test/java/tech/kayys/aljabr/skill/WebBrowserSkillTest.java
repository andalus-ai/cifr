package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebBrowserSkillTest {

    @Test
    void testNavigateMissingUrl() {
        WebBrowserSkill skill = new WebBrowserSkill();
        Map<String, Object> input = Map.of("action", "navigate");

        Uni<Map<String, Object>> resultUni = skill.execute(input);
        Map<String, Object> result = resultUni.await().indefinitely();

        assertEquals("error", result.get("status"));
        assertEquals("url is required for navigate", result.get("message"));
    }

    @Test
    void testCanHandle() {
        WebBrowserSkill skill = new WebBrowserSkill();
        assertTrue(skill.canHandle(Map.of("action", "navigate")));
        assertFalse(skill.canHandle(Map.of("other", "value")));
    }
}
