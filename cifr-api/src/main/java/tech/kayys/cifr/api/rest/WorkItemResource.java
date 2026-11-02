package tech.kayys.cifr.api.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.cifr.engine.work.WorkItem;
import tech.kayys.cifr.engine.work.WorkStatus;
import tech.kayys.cifr.engine.work.WorkType;
import tech.kayys.cifr.engine.work.TimelineEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Path("/api/work-items")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class WorkItemResource {

    private final Map<String, WorkItem> items = new ConcurrentHashMap<String, WorkItem>();
    private final Map<String, List<TimelineEvent>> timelines = new ConcurrentHashMap<String, List<TimelineEvent>>();

    public WorkItemResource() {
        WorkItem defaultItem = new WorkItem(
                "WI-1",
                "default",
                WorkType.FEATURE,
                "Agentic Coding Execution",
                "Execute workspace modifications and verification loop",
                WorkStatus.IN_PROGRESS,
                Instant.now(),
                Instant.now()
        );
        items.put(defaultItem.id(), defaultItem);
    }

    @GET
    public List<WorkItem> list() {
        return new ArrayList<>(items.values());
    }

    @POST
    public Response create(WorkItem item) {
        if (item == null || item.id() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        items.put(item.id(), item);
        return Response.status(Response.Status.CREATED).entity(item).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        WorkItem item = items.get(id);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(item).build();
    }

    @GET
    @Path("/{id}/timeline")
    public List<TimelineEvent> getTimeline(@PathParam("id") String id) {
        return timelines.getOrDefault(id, List.of());
    }
}
