package tech.kayys.cifr.api.rest;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.cifr.engine.skill.management.SkillManagerService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint for runtime skill management, hot-reload, and soft-delete/trash.
 */
@Path("/api/v1/skills")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SkillManagementResource {

    @Inject
    public SkillManagerService skillManagerService;

    @GET
    public Uni<Response> listSkills(
            @QueryParam("category") String category,
            @QueryParam("query") String query,
            @QueryParam("includeTrash") @DefaultValue("false") boolean includeTrash) {

        return Uni.createFrom().item(() -> {
            var list = skillManagerService.listSkills(category, query, includeTrash);
            return Response.ok(Map.of(
                    "skills", list,
                    "totalCount", list.size()
            )).build();
        });
    }

    @GET
    @Path("/trash")
    public Uni<Response> listTrash() {
        return Uni.createFrom().item(() -> {
            var trashed = skillManagerService.listTrash();
            return Response.ok(Map.of(
                    "skills", trashed,
                    "totalCount", trashed.size()
            )).build();
        });
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getSkill(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            var detailOpt = skillManagerService.getSkill(id);
            if (detailOpt.isPresent()) {
                return Response.ok(detailOpt.get()).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Skill not found: " + id))
                        .build();
            }
        });
    }

    @POST
    public Uni<Response> createSkill(SkillManagerService.SkillInputDto input) {
        return Uni.createFrom().item(() -> {
            var res = skillManagerService.saveSkill(input);
            if (res.success()) {
                return Response.status(Response.Status.CREATED).entity(res).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(res).build();
            }
        });
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateSkill(@PathParam("id") String id, SkillManagerService.SkillInputDto input) {
        return Uni.createFrom().item(() -> {
            SkillManagerService.SkillInputDto payload = new SkillManagerService.SkillInputDto(
                    id,
                    input.name(),
                    input.description(),
                    input.category(),
                    input.reasoningMode(),
                    input.mutatesWorkspace(),
                    input.allowedTools(),
                    input.instructions(),
                    input.references(),
                    input.scripts()
            );
            var res = skillManagerService.saveSkill(payload);
            if (res.success()) {
                return Response.ok(res).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(res).build();
            }
        });
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteSkill(
            @PathParam("id") String id,
            @QueryParam("hard") @DefaultValue("false") boolean hard) {

        return Uni.createFrom().item(() -> {
            var res = skillManagerService.deleteSkill(id, hard);
            if (res.success()) {
                return Response.ok(res).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity(res).build();
            }
        });
    }

    @POST
    @Path("/{id}/restore")
    public Uni<Response> restoreSkill(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            var res = skillManagerService.restoreSkill(id);
            if (res.success()) {
                return Response.ok(res).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity(res).build();
            }
        });
    }

    @POST
    @Path("/reload")
    public Uni<Response> reloadSkills() {
        return Uni.createFrom().item(() -> {
            int count = skillManagerService.reloadSkills();
            return Response.ok(Map.of(
                    "success", true,
                    "reloadedCount", count,
                    "message", "Successfully reloaded " + count + " skills from disk"
            )).build();
        });
    }
}
