package tech.kayys.cifr.engine.skill.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.cifr.engine.skill.DefaultSkillRegistry;
import tech.kayys.cifr.engine.skill.loader.AndalusSkillDirectoryLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SkillManagerServiceTest {

    @TempDir
    Path tempHome;

    private SkillManagerService managerService;
    private DefaultSkillRegistry registry;
    private AndalusSkillDirectoryLoader loader;

    @BeforeEach
    void setUp() {
        System.setProperty("user.home", tempHome.toAbsolutePath().toString());

        registry = new DefaultSkillRegistry();
        loader = new AndalusSkillDirectoryLoader();
        loader.skillRegistry = registry;

        managerService = new SkillManagerService();
        managerService.skillRegistry = registry;
        managerService.directoryLoader = loader;
    }

    @Test
    void testCreateModifySoftDeleteAndRestoreLifecycle() throws IOException {
        // 1. Create a new skill
        SkillManagerService.SkillInputDto input = new SkillManagerService.SkillInputDto(
                "custom-slack-notifier",
                "Slack Notifier",
                "Sends notifications to Slack channels",
                "MODIFICATION",
                "REQUIRED",
                true,
                List.of("slack_post_message", "workspace.read"),
                "# Slack Instructions\nPost messages when tasks complete.",
                Map.of("slack-guide.md", "How to use Slack webhook"),
                Map.of("notify.sh", "echo 'notifying'")
        );

        var saveResult = managerService.saveSkill(input);
        assertTrue(saveResult.success());
        assertEquals("custom-slack-notifier", saveResult.skillId());

        // Verify registered in registry
        Optional<SkillManagerService.SkillDetailDto> detailOpt = managerService.getSkill("custom-slack-notifier");
        assertTrue(detailOpt.isPresent());
        var detail = detailOpt.get();
        assertEquals("Slack Notifier", detail.summary().name());
        assertEquals("MODIFICATION", detail.summary().category());
        assertTrue(detail.summary().mutatesWorkspace());
        assertEquals("How to use Slack webhook", detail.references().get("slack-guide.md"));
        assertEquals(1, detail.scripts().size());

        // 2. Modify the skill at runtime
        SkillManagerService.SkillInputDto updatedInput = new SkillManagerService.SkillInputDto(
                "custom-slack-notifier",
                "Slack Notifier v2",
                "Enhanced Slack notifier with thread support",
                "MODIFICATION",
                "REQUIRED",
                true,
                List.of("slack_post_message"),
                "# Slack v2\nPost to thread.",
                Map.of("slack-guide.md", "Updated guide"),
                Map.of()
        );

        var updateResult = managerService.saveSkill(updatedInput);
        assertTrue(updateResult.success());

        var updatedDetail = managerService.getSkill("custom-slack-notifier").get();
        assertEquals("Slack Notifier v2", updatedDetail.summary().name());
        assertEquals("Enhanced Slack notifier with thread support", updatedDetail.summary().description());
        assertEquals("Updated guide", updatedDetail.references().get("slack-guide.md"));

        // 3. Soft Delete (Move to Trash)
        var deleteResult = managerService.deleteSkill("custom-slack-notifier", false);
        assertTrue(deleteResult.success());
        assertTrue(deleteResult.movedToTrash());

        // Verify unregistered from active skills
        assertTrue(registry.find("custom-slack-notifier").isEmpty());
        assertTrue(managerService.listSkills(null, null, false).isEmpty());

        // Verify it is present in Trash
        List<SkillManagerService.SkillSummary> trashList = managerService.listTrash();
        assertEquals(1, trashList.size());
        assertEquals("custom-slack-notifier", trashList.get(0).id());
        assertTrue(trashList.get(0).inTrash());

        // 4. Restore from Trash
        var restoreResult = managerService.restoreSkill("custom-slack-notifier");
        assertTrue(restoreResult.success());

        // Verify re-registered in active registry
        assertTrue(registry.find("custom-slack-notifier").isPresent());
        assertEquals(1, managerService.listSkills(null, null, false).size());
        assertTrue(managerService.listTrash().isEmpty());
    }

    @Test
    void testHardDelete() {
        SkillManagerService.SkillInputDto input = new SkillManagerService.SkillInputDto(
                "temp-disposable-skill",
                "Disposable Skill",
                "To be deleted permanently",
                "DISCOVERY",
                "NONE",
                false,
                List.of(),
                "Disposable instructions",
                Map.of(),
                Map.of()
        );

        managerService.saveSkill(input);
        assertTrue(registry.find("temp-disposable-skill").isPresent());

        // Hard delete
        var deleteResult = managerService.deleteSkill("temp-disposable-skill", true);
        assertTrue(deleteResult.success());
        assertFalse(deleteResult.movedToTrash());

        assertTrue(registry.find("temp-disposable-skill").isEmpty());
        assertTrue(managerService.listTrash().isEmpty());
    }
}
