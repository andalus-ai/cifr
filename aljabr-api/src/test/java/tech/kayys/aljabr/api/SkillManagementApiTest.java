package tech.kayys.aljabr.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.aljabr.api.grpc.SkillServiceImpl;
import tech.kayys.aljabr.api.rest.SkillManagementResource;
import tech.kayys.aljabr.engine.skill.DefaultSkillRegistry;
import tech.kayys.aljabr.engine.skill.loader.AndalusSkillDirectoryLoader;
import tech.kayys.aljabr.engine.skill.management.SkillManagerService;
import tech.kayys.andalus.pro.api.grpc.*;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillManagementApiTest {

    @TempDir
    Path tempHome;

    private SkillManagerService managerService;
    private SkillManagementResource restResource;
    private SkillServiceImpl grpcService;

    @BeforeEach
    void setUp() {
        System.setProperty("user.home", tempHome.toAbsolutePath().toString());

        DefaultSkillRegistry registry = new DefaultSkillRegistry();
        AndalusSkillDirectoryLoader loader = new AndalusSkillDirectoryLoader();
        loader.skillRegistry = registry;

        managerService = new SkillManagerService();
        managerService.skillRegistry = registry;
        managerService.directoryLoader = loader;

        restResource = new SkillManagementResource();
        restResource.skillManagerService = managerService;

        grpcService = new SkillServiceImpl();
        grpcService.skillManager = managerService;
    }

    @Test
    void testRestEndpointCrudAndTrash() {
        // 1. Create skill via REST
        SkillManagerService.SkillInputDto input = new SkillManagerService.SkillInputDto(
                "rest-test-skill",
                "REST Test Skill",
                "Skill created via REST API",
                "UNDERSTANDING",
                "NONE",
                false,
                List.of("read_file"),
                "# Instructions\nREST skill instructions",
                Map.of(),
                Map.of()
        );

        Response createResp = restResource.createSkill(input).await().indefinitely();
        assertEquals(201, createResp.getStatus());

        // 2. List skills via REST
        Response listResp = restResource.listSkills(null, null, false).await().indefinitely();
        assertEquals(200, listResp.getStatus());
        Map<?, ?> listBody = (Map<?, ?>) listResp.getEntity();
        assertEquals(1, listBody.get("totalCount"));

        // 3. Get skill details via REST
        Response getResp = restResource.getSkill("rest-test-skill").await().indefinitely();
        assertEquals(200, getResp.getStatus());
        SkillManagerService.SkillDetailDto detail = (SkillManagerService.SkillDetailDto) getResp.getEntity();
        assertEquals("REST Test Skill", detail.summary().name());

        // 4. Update skill via REST
        SkillManagerService.SkillInputDto updatedInput = new SkillManagerService.SkillInputDto(
                "rest-test-skill",
                "Updated REST Skill",
                "Updated description",
                "UNDERSTANDING",
                "NONE",
                false,
                List.of("read_file"),
                "# Updated Instructions",
                Map.of(),
                Map.of()
        );
        Response updateResp = restResource.updateSkill("rest-test-skill", updatedInput).await().indefinitely();
        assertEquals(200, updateResp.getStatus());

        // 5. Soft delete via REST (move to trash)
        Response deleteResp = restResource.deleteSkill("rest-test-skill", false).await().indefinitely();
        assertEquals(200, deleteResp.getStatus());

        // Verify active list is empty
        Response emptyListResp = restResource.listSkills(null, null, false).await().indefinitely();
        assertEquals(0, ((Map<?, ?>) emptyListResp.getEntity()).get("totalCount"));

        // Verify in trash via REST
        Response trashResp = restResource.listTrash().await().indefinitely();
        assertEquals(200, trashResp.getStatus());
        assertEquals(1, ((Map<?, ?>) trashResp.getEntity()).get("totalCount"));

        // 6. Restore from trash via REST
        Response restoreResp = restResource.restoreSkill("rest-test-skill").await().indefinitely();
        assertEquals(200, restoreResp.getStatus());

        // Verify active again
        Response restoredListResp = restResource.listSkills(null, null, false).await().indefinitely();
        assertEquals(1, ((Map<?, ?>) restoredListResp.getEntity()).get("totalCount"));
    }

    @Test
    void testGrpcServiceCrudAndTrash() {
        // 1. Create skill via gRPC
        SaveSkillRequest saveReq = SaveSkillRequest.newBuilder()
                .setId("grpc-test-skill")
                .setName("gRPC Test Skill")
                .setDescription("Skill created via gRPC service")
                .setCategory("PLANNING")
                .setReasoningMode("REQUIRED")
                .setMutatesWorkspace(false)
                .addAllowedTools("create_plan")
                .setInstructions("# gRPC Planning Skill Instructions")
                .putReferences("plan-template.md", "Template content")
                .build();

        SaveSkillResponse saveResp = grpcService.saveSkill(saveReq).await().indefinitely();
        assertTrue(saveResp.getSuccess());
        assertEquals("grpc-test-skill", saveResp.getSkillId());

        // 2. Get skill via gRPC
        GetSkillResponse getResp = grpcService.getSkill(GetSkillRequest.newBuilder()
                .setSkillId("grpc-test-skill")
                .build()).await().indefinitely();
        assertTrue(getResp.getFound());
        assertEquals("gRPC Test Skill", getResp.getSkill().getInfo().getName());
        assertEquals("PLANNING", getResp.getSkill().getInfo().getCategory());
        assertEquals("Template content", getResp.getSkill().getReferencesMap().get("plan-template.md"));

        // 3. List skills via gRPC
        ListSkillsResponse listResp = grpcService.listSkills(ListSkillsRequest.newBuilder().build()).await().indefinitely();
        assertEquals(1, listResp.getTotalCount());
        assertEquals("grpc-test-skill", listResp.getSkills(0).getId());

        // 4. Soft delete via gRPC
        DeleteSkillResponse deleteResp = grpcService.deleteSkill(DeleteSkillRequest.newBuilder()
                .setSkillId("grpc-test-skill")
                .setHardDelete(false)
                .build()).await().indefinitely();
        assertTrue(deleteResp.getSuccess());
        assertTrue(deleteResp.getMovedToTrash());

        // Verify trash via gRPC
        ListSkillsResponse trashResp = grpcService.listTrash(Empty.newBuilder().build()).await().indefinitely();
        assertEquals(1, trashResp.getTotalCount());
        assertEquals("grpc-test-skill", trashResp.getSkills(0).getId());
        assertTrue(trashResp.getSkills(0).getInTrash());

        // 5. Restore via gRPC
        RestoreSkillResponse restoreResp = grpcService.restoreSkill(RestoreSkillRequest.newBuilder()
                .setSkillId("grpc-test-skill")
                .build()).await().indefinitely();
        assertTrue(restoreResp.getSuccess());

        // Verify active list via gRPC
        ListSkillsResponse activeResp = grpcService.listSkills(ListSkillsRequest.newBuilder().build()).await().indefinitely();
        assertEquals(1, activeResp.getTotalCount());
        assertFalse(activeResp.getSkills(0).getInTrash());

        // 6. Reload via gRPC
        ReloadSkillsResponse reloadResp = grpcService.reloadSkills(Empty.newBuilder().build()).await().indefinitely();
        assertTrue(reloadResp.getSuccess());
        assertEquals(1, reloadResp.getReloadedCount());
    }
}
