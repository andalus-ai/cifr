package tech.kayys.cifr.engine.index.indexer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.index.DefaultFileIndex;
import tech.kayys.cifr.engine.index.dependency.DefaultDependencyIndex;
import tech.kayys.cifr.engine.index.relation.DefaultRelationIndex;
import tech.kayys.cifr.engine.index.symbol.DefaultSymbolIndex;
import tech.kayys.cifr.engine.index.symbol.SymbolId;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceIndexerTest {

    private Path tempWorkspace;
    private DefaultFileIndex fileIndex;
    private DefaultSymbolIndex symbolIndex;
    private DefaultDependencyIndex dependencyIndex;
    private DefaultRelationIndex relationIndex;
    private WorkspaceIndexer workspaceIndexer;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("indexer-test-");
        fileIndex = new DefaultFileIndex();
        symbolIndex = new DefaultSymbolIndex();
        dependencyIndex = new DefaultDependencyIndex();
        relationIndex = new DefaultRelationIndex();

        workspaceIndexer = new DefaultWorkspaceIndexer(fileIndex, symbolIndex, dependencyIndex, relationIndex);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempWorkspace != null && Files.exists(tempWorkspace)) {
            try (var stream = Files.walk(tempWorkspace)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void testFullWorkspaceScanAndIndex() throws IOException {
        Path srcDir = tempWorkspace.resolve("src");
        Files.createDirectories(srcDir);

        Path authService = srcDir.resolve("AuthService.java");
        Files.writeString(authService,
            "package tech.kayys.auth;\n" +
            "import tech.kayys.core.Token;\n" +
            "import java.util.List;\n" +
            "\n" +
            "public class AuthService {\n" +
            "    public boolean authenticate(String token) {\n" +
            "        return true;\n" +
            "    }\n" +
            "}\n");

        Path userModel = srcDir.resolve("User.dart");
        Files.writeString(userModel,
            "class User {\n" +
            "  final String id;\n" +
            "  User(this.id);\n" +
            "}\n");

        Path readme = tempWorkspace.resolve("README.md");
        Files.writeString(readme, "# Project Documentation\n");

        IndexResult result = workspaceIndexer.index(tempWorkspace, "proj-1").await().indefinitely();

        assertNotNull(result);
        assertEquals(3, result.filesIndexed());
        assertTrue(result.symbolsIndexed() >= 2); // AuthService, authenticate, User
        assertTrue(result.dependenciesIndexed() >= 2); // imports
        assertNotNull(result.fingerprint());
        assertTrue(result.durationMs() >= 0);

        // Verify query capability on indexed symbols
        SymbolInfo authSym = symbolIndex.getSymbol(new SymbolId("AuthService", "", "")).await().indefinitely();
        assertNotNull(authSym);
        assertEquals("AuthService", authSym.name());

        List<SymbolInfo> searchMatches = symbolIndex.searchSymbols("Auth").await().indefinitely();
        assertFalse(searchMatches.isEmpty());
    }

    @Test
    void testIncrementalFileUpdate() throws IOException {
        Path srcDir = tempWorkspace.resolve("src");
        Files.createDirectories(srcDir);

        Path file = srcDir.resolve("TokenValidator.java");
        Files.writeString(file,
            "public class TokenValidator {\n" +
            "    public boolean isValid(String token) { return true; }\n" +
            "}\n");

        workspaceIndexer.updateFile(Path.of("src/TokenValidator.java"), tempWorkspace, "proj-1").await().indefinitely();

        SymbolInfo valSym = symbolIndex.getSymbol(new SymbolId("TokenValidator", "", "")).await().indefinitely();
        assertNotNull(valSym);
        assertEquals("TokenValidator", valSym.name());
    }
}
