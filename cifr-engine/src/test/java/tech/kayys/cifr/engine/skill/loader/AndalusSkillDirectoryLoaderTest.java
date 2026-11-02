package tech.kayys.cifr.engine.skill.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.cifr.engine.skill.DefaultSkillRegistry;
import tech.kayys.cifr.engine.skill.ReasoningMode;
import tech.kayys.cifr.engine.skill.SkillCategory;
import tech.kayys.cifr.engine.skill.impl.MarkdownSkill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AndalusSkillDirectoryLoaderTest {

    @Test
    void testLoadSkillFromDirectoryWithFrontmatter(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("test-deploy-skill");
        Files.createDirectories(skillDir);

        String skillMdContent = """
                ---
                name: test-deploy-skill
                description: Deploys the service to staging
                category: MODIFICATION
                reasoningMode: REQUIRED
                mutatesWorkspace: true
                ---
                
                # Deploy Instructions
                1. Build artifact
                2. Run smoke tests
                3. Push image
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillMdContent);

        // Add reference file
        Path refDir = skillDir.resolve("references");
        Files.createDirectories(refDir);
        Files.writeString(refDir.resolve("guide.md"), "Deployment guide content");

        AndalusSkillDirectoryLoader loader = new AndalusSkillDirectoryLoader();
        loader.skillRegistry = new DefaultSkillRegistry();

        Optional<MarkdownSkill> skillOpt = loader.loadSkillFromPath(skillDir);
        assertTrue(skillOpt.isPresent());

        MarkdownSkill skill = skillOpt.get();
        assertEquals("test-deploy-skill", skill.id().value());
        assertEquals("test-deploy-skill", skill.metadata().name());
        assertEquals("Deploys the service to staging", skill.metadata().description());
        assertEquals(SkillCategory.MODIFICATION, skill.metadata().category());
        assertEquals(ReasoningMode.REQUIRED, skill.metadata().reasoningMode());
        assertTrue(skill.metadata().mutatesWorkspace());
        assertTrue(skill.instructions().contains("Deploy Instructions"));
        assertEquals("Deployment guide content", skill.references().get("guide.md"));
    }

    @Test
    void testLoadsRealAndalusUserSkillsIfPresent() {
        Path andalusSkills = Paths.get(System.getProperty("user.home"), ".andalus", "skills");
        if (Files.isDirectory(andalusSkills)) {
            AndalusSkillDirectoryLoader loader = new AndalusSkillDirectoryLoader();
            loader.skillRegistry = new DefaultSkillRegistry();

            int loaded = loader.loadAll();
            assertTrue(loaded > 0, "Should load skills from ~/.andalus/skills");

            var descriptors = loader.skillRegistry.listDescriptors();
            assertFalse(descriptors.isEmpty());
            System.out.println("Successfully loaded " + loaded + " user skills from ~/.andalus/skills");
        }
    }
}
