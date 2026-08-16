package tech.kayys.aljabr.engine.skill;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.dependency.Dependency;
import tech.kayys.aljabr.engine.index.dependency.DependencyKind;
import tech.kayys.aljabr.engine.index.dependency.DependencyRequest;
import tech.kayys.aljabr.engine.index.reference.Reference;
import tech.kayys.aljabr.engine.index.reference.ReferenceKind;
import tech.kayys.aljabr.engine.index.reference.ReferenceRequest;
import tech.kayys.aljabr.engine.index.relation.RelatedFile;
import tech.kayys.aljabr.engine.index.relation.RelatedFileRequest;
import tech.kayys.aljabr.engine.index.relation.RelationReason;
import tech.kayys.aljabr.engine.index.search.SearchMatch;
import tech.kayys.aljabr.engine.index.search.SearchMetadata;
import tech.kayys.aljabr.engine.index.search.SearchRequest;
import tech.kayys.aljabr.engine.index.search.SearchResult;
import tech.kayys.aljabr.engine.index.search.SearchStrategy;
import tech.kayys.aljabr.engine.index.symbol.SourceRange;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.index.symbol.SymbolInfo;
import tech.kayys.aljabr.engine.index.symbol.SymbolKind;
import tech.kayys.aljabr.engine.skill.impl.CodeExplainSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeInspectSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeSearchSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeTraceSkill;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ComposableCodingSkillsTest {

    private SkillRegistry skillRegistry;
    private SkillRouter skillRouter;
    private RepositoryIntelligence mockIntelligence;

    @BeforeEach
    void setUp() {
        mockIntelligence = new RepositoryIntelligence() {
            @Override
            public Uni<Void> initializeWorkspace(Path workspaceRoot) {
                return Uni.createFrom().voidItem();
            }

            @Override
            public Uni<tech.kayys.aljabr.engine.index.WorkspaceFingerprint> snapshot(Path workspaceRoot) {
                return Uni.createFrom().nullItem();
            }

            @Override
            public Uni<SearchResult> search(SearchRequest request, Path workspaceRoot, String workspaceId) {
                SearchMatch match = new SearchMatch(Path.of("src/AuthService.java"), 10, "public class AuthService", 0.95);
                SearchMetadata meta = new SearchMetadata(1, false, SearchStrategy.TEXT);
                return Uni.createFrom().item(new SearchResult(List.of(match), meta));
            }

            @Override
            public Uni<SymbolInfo> symbol(SymbolId id) {
                SourceRange range = new SourceRange(10, 25, 1, 1);
                SymbolInfo info = new SymbolInfo(id, id.qualifiedName(), SymbolKind.CLASS, Path.of("src/AuthService.java"), range, Optional.empty());
                return Uni.createFrom().item(info);
            }

            @Override
            public Uni<List<Reference>> references(ReferenceRequest request) {
                SourceRange range = new SourceRange(50, 50, 4, 20);
                Reference ref = new Reference(request.targetSymbol(), Path.of("src/AuthServiceTest.java"), range, ReferenceKind.CALL);
                return Uni.createFrom().item(List.of(ref));
            }

            @Override
            public Uni<List<Dependency>> dependencies(DependencyRequest request) {
                Dependency dep = new Dependency("tech.kayys:aljabr", "io.quarkus:quarkus-grpc", DependencyKind.LIBRARY);
                return Uni.createFrom().item(List.of(dep));
            }

            @Override
            public Uni<List<RelatedFile>> related(RelatedFileRequest request) {
                RelatedFile rf = new RelatedFile(Path.of("src/AuthServiceTest.java"), 0.9, List.of(RelationReason.TEST_TARGET));
                return Uni.createFrom().item(List.of(rf));
            }
        };

        skillRegistry = new DefaultSkillRegistry();
        skillRegistry.register(new CodeSearchSkill(mockIntelligence));
        skillRegistry.register(new CodeInspectSkill(mockIntelligence));
        skillRegistry.register(new CodeTraceSkill(mockIntelligence));
        skillRegistry.register(new CodeExplainSkill(mockIntelligence));

        skillRouter = new SkillRouter(skillRegistry);
    }

    @Test
    void testSkillRegistryListingAndFinding() {
        List<SkillDescriptor> descriptors = skillRegistry.listDescriptors();
        assertEquals(4, descriptors.size());

        assertTrue(skillRegistry.find("code.search").isPresent());
        assertEquals(SkillCategory.DISCOVERY, skillRegistry.find("code.search").get().descriptor().category());
        assertEquals(ReasoningMode.NONE, skillRegistry.find("code.search").get().descriptor().reasoningMode());

        assertTrue(skillRegistry.find("code.inspect").isPresent());
        assertEquals(SkillCategory.ANALYSIS, skillRegistry.find("code.inspect").get().descriptor().category());
        assertEquals(ReasoningMode.OPTIONAL, skillRegistry.find("code.inspect").get().descriptor().reasoningMode());

        assertTrue(skillRegistry.find("code.trace").isPresent());
        assertEquals(SkillCategory.ANALYSIS, skillRegistry.find("code.trace").get().descriptor().category());
        assertEquals(ReasoningMode.NONE, skillRegistry.find("code.trace").get().descriptor().reasoningMode());

        assertTrue(skillRegistry.find("code.explain").isPresent());
        assertEquals(SkillCategory.EXPLANATION, skillRegistry.find("code.explain").get().descriptor().category());
        assertEquals(ReasoningMode.REQUIRED, skillRegistry.find("code.explain").get().descriptor().reasoningMode());
    }

    @Test
    void testCodeSearchSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", Path.of("."), Map.of("query", "AuthService"));
        SkillResult result = skillRouter.dispatch("code.search", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Found 1 matches"));
        assertEquals(1, result.evidenceIds().size());
        assertEquals("src/AuthService.java", result.evidenceIds().get(0));
    }

    @Test
    void testCodeInspectSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", Path.of("."), Map.of("symbol", "AuthService", "path", "src/AuthService.java"));
        SkillResult result = skillRouter.dispatch("code.inspect", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Inspected CLASS 'AuthService'"));
        assertEquals(true, result.outputData().get("found"));
        assertEquals(10, result.outputData().get("lineStart"));
    }

    @Test
    void testCodeTraceSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", Path.of("."), Map.of("symbol", "authenticate", "path", "src/AuthService.java"));
        SkillResult result = skillRouter.dispatch("code.trace", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Traced 1 references"));
        assertEquals(1, result.outputData().get("referenceCount"));
        assertTrue(result.evidenceIds().contains("src/AuthServiceTest.java"));
    }

    @Test
    void testCodeExplainSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", Path.of("."), Map.of("symbol", "AuthService", "path", "src/AuthService.java"));
        SkillResult result = skillRouter.dispatch("code.explain", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Context prepared for model explanation"));
        assertEquals(true, result.outputData().get("requiresModel"));
    }

    @Test
    void testSkillRouterUnknownSkillGracefulFailure() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", Path.of("."), Map.of());
        SkillResult result = skillRouter.dispatch("nonexistent.skill", ctx).await().indefinitely();

        assertFalse(result.isSuccess());
        assertTrue(result.summary().contains("Skill not found"));
    }
}
