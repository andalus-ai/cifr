package tech.kayys.aljabr.engine.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.aljabr.engine.patch.MutationStore;

import java.util.List;

@Path("/api/mutations")
@ApplicationScoped
public class MutationResource {

    @Inject
    MutationStore mutationStore;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listMutations(@QueryParam("workspace") String workspace) {
        // workspace param currently ignored — MutationStore reads workspace root.
        return mutationStore.listApplied();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response readMutation(@PathParam("id") String id) {
        String content = mutationStore.readRecord(id);
        if (content == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(content).build();
    }
}
