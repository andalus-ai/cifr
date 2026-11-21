package tech.kayys.cifr.engine.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.index.dependency.DependencyRequest;
import tech.kayys.cifr.engine.index.dependency.DependencyKind;
import tech.kayys.cifr.engine.index.dependency.DefaultDependencyIndex;
import tech.kayys.cifr.engine.index.reference.DefaultReferenceIndex;
import tech.kayys.cifr.engine.index.reference.ReferenceRequest;
import tech.kayys.cifr.engine.index.relation.DefaultRelationIndex;
import tech.kayys.cifr.engine.index.relation.RelatedFileRequest;
import tech.kayys.cifr.engine.index.symbol.DefaultSymbolIndex;
import tech.kayys.cifr.engine.index.symbol.SourceRange;
import tech.kayys.cifr.engine.index.symbol.SymbolId;
import tech.kayys.cifr.engine.index.symbol.SymbolInfo;
import tech.kayys.cifr.engine.index.symbol.SymbolKind;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RepositoryIntelligenceIntegrationTest {

    private DefaultRepositoryIntelligence repositoryIntelligence;
    private DefaultFileIndex fileIndex;
    private DefaultSymbolIndex symbolIndex;
    private DefaultReferenceIndex referenceIndex;
    private SymbolId target;

    @BeforeEach
    public void setup() {
        repositoryIntelligence = new DefaultRepositoryIntelligence();
        fileIndex = new DefaultFileIndex();
        symbolIndex = new DefaultSymbolIndex();
        referenceIndex = new DefaultReferenceIndex();
        target = new SymbolId("MockClass.mockMethod", "mockMethod", "MockClass.java");

        fileIndex.registerFile(new FileRecord(
                Path.of("MockClass.java"), "hash", 128, "java", false, false, FileRole.SOURCE));
        symbolIndex.registerSymbol(new SymbolInfo(
                target, "mockMethod", SymbolKind.METHOD, Path.of("MockClass.java"),
                new SourceRange(10, 10, 0, 20), Optional.empty()));
        symbolIndex.registerSymbol(new SymbolInfo(
                target, "mockMethod", SymbolKind.METHOD, Path.of("MockClass.java"),
                new SourceRange(30, 30, 0, 20), Optional.empty(), "other-workspace"));

        repositoryIntelligence.fileIndex = fileIndex;
        repositoryIntelligence.symbolIndex = symbolIndex;
        repositoryIntelligence.referenceIndex = referenceIndex;
        repositoryIntelligence.dependencyIndex = new DefaultDependencyIndex();
        repositoryIntelligence.relationIndex = new DefaultRelationIndex();

        // The unit test deliberately has no running IDE host. The reference index
        // must safely return no results rather than fabricate a definition location.
        setField(referenceIndex, "fileIndex", fileIndex);
        setField(referenceIndex, "symbolIndex", symbolIndex);
    }

    @Test
    public void testSymbolResolution() {
        SymbolInfo info = repositoryIntelligence.symbol(target).await().indefinitely();
        assertNotNull(info);
        assertEquals("mockMethod", info.name());
        assertEquals("default-workspace", info.workspaceId());
    }

    @Test
    public void testWorkspaceAwareSymbolLookup() {
        SymbolInfo defaultWorkspace = repositoryIntelligence.symbol(target, "default-workspace")
                .await().indefinitely();
        SymbolInfo otherWorkspace = repositoryIntelligence.symbol(target, "other-workspace")
                .await().indefinitely();

        assertNotNull(defaultWorkspace);
        assertNotNull(otherWorkspace);
        assertEquals(10, defaultWorkspace.range().startLine());
        assertEquals(30, otherWorkspace.range().startLine());
    }

    @Test
    public void testReferenceResolutionWithoutIdeHost() {
        ReferenceRequest request = new ReferenceRequest(target, 10);
        var references = repositoryIntelligence.references(request).await().indefinitely();
        assertNotNull(references);
        assertTrue(references.isEmpty(), "references require a live CIFR IDE resolver");
    }

    @Test
    public void testDependencyResolution() {
        DependencyRequest request = new DependencyRequest("test", DependencyKind.MODULE);
        var deps = repositoryIntelligence.dependencies(request).await().indefinitely();
        assertNotNull(deps);
        assertTrue(deps.isEmpty());
    }

    @Test
    public void testRelationResolution() {
        RelatedFileRequest request = new RelatedFileRequest(Path.of("test.java"), 10);
        var relations = repositoryIntelligence.related(request).await().indefinitely();
        assertNotNull(relations);
        assertTrue(relations.isEmpty());
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to configure test field " + name, e);
        }
    }
}
