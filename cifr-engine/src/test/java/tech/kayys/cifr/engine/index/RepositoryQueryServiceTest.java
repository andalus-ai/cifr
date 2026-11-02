package tech.kayys.cifr.engine.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RepositoryQueryServiceTest {

    private DefaultRepositoryQueryService queryService;

    @BeforeEach
    public void setup() {
        queryService = new DefaultRepositoryQueryService();
    }

    @Test
    public void testFileRankingByTaskRelevance() {
        String task = "Fix expiration boundary bug in TokenValidator.java";
        List<String> candidateFiles = List.of(
            "src/main/java/tech/kayys/auth/TokenValidator.java",
            "src/main/java/tech/kayys/database/ConnectionPool.java",
            "src/main/java/tech/kayys/config/AppConfig.java"
        );

        List<FileImportance> ranked = queryService.rankFiles(task, candidateFiles);

        assertEquals(3, ranked.size());

        // TokenValidator matches directly in the task description
        FileImportance first = ranked.get(0);
        assertEquals("src/main/java/tech/kayys/auth/TokenValidator.java", first.fileId());
        assertEquals(1.0, first.score());
        assertEquals(ImportanceReason.DIRECTLY_REFERENCED_BY_TASK, first.primaryReason());

        // Unmatched files receive lower baseline score
        FileImportance second = ranked.get(1);
        assertEquals(0.5, second.score());
        assertEquals(ImportanceReason.DEPENDENCY_PROXIMITY, second.primaryReason());
    }

    @Test
    public void testRankFilesEmptyInputs() {
        List<FileImportance> empty = queryService.rankFiles("Some task", List.of());
        assertTrue(empty.isEmpty());

        List<FileImportance> nullList = queryService.rankFiles("Some task", null);
        assertTrue(nullList.isEmpty());
    }
}
