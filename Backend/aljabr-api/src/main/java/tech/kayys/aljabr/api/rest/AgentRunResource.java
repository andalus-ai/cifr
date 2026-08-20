package tech.kayys.aljabr.engine.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;
import tech.kayys.aljabr.orchestration.CoderOrchestrator;
import tech.kayys.aljabr.engine.event.DefaultEventLedger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Path("/api/agent-runs")
@ApplicationScoped
public class AgentRunResource {

    @Inject
    GitRepositoryService gitService;

    @Inject
    Sse sse;

    @Inject
    CoderOrchestrator orchestrator;

    @Inject
    DefaultEventLedger eventLedger;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @POST
    @Path("start")
    @Consumes(MediaType.APPLICATION_JSON)
    public void startRun(Map<String, Object> payload, @Context SseEventSink sink) {
        if (sink == null || sink.isClosed()) return;

        final String request = (String) payload.getOrDefault("request", "agent-run-request");

        // Register a listener on the event ledger to forward events as SSE to the client
        java.util.function.Consumer<tech.kayys.aljabr.api.agent.AgentEvent> listener = ev -> {
            try {
                if (sink == null || sink.isClosed()) return;
                Map<String, Object> out = new HashMap<>();
                out.put("event", ev.getClass().getSimpleName());
                // convert the record into a map for stable JSON shape
                Map data = mapper.convertValue(ev, Map.class);
                out.put("data", data);
                String payloadJson = mapper.writeValueAsString(out);
                sink.send(sse.newEventBuilder().name("agent-event").data(String.class, payloadJson).build());
            } catch (Exception ex) {
                // ignore send errors
            }
        };

        eventLedger.registerListener(listener);

        // Build a request and start actual orchestrator -> coding loop
        Map<String, Object> params = new HashMap<>();
        params.put("request", request);
        tech.kayys.wayang.agent.AgentRequest agentRequest = tech.kayys.wayang.agent.AgentRequest.builder()
                .sessionId((String) payload.getOrDefault("sessionId", "local-session"))
                .content(request)
                .build();

        // Run orchestrator asynchronously and close stream when done
        orchestrator.execute(agentRequest)
                .subscribe().with(response -> {
                    try {
                        if (sink == null || sink.isClosed()) return;
                        Map<String, Object> out = new HashMap<>();
                        out.put("event", "done");
                        out.put("result", mapper.convertValue(response.metadata(), Map.class));
                        String payloadJson = mapper.writeValueAsString(out);
                        sink.send(sse.newEventBuilder().name("done").data(String.class, payloadJson).build());
                        sink.close();
                    } catch (Exception ex) {
                        // noop
                    } finally {
                        eventLedger.unregisterListener(listener);
                    }
                }, err -> {
                    try {
                        if (sink == null || sink.isClosed()) return;
                        Map<String, Object> out = new HashMap<>();
                        out.put("event", "error");
                        out.put("message", err.getMessage());
                        String payloadJson = mapper.writeValueAsString(out);
                        sink.send(sse.newEventBuilder().name("error").data(String.class, payloadJson).build());
                        sink.close();
                    } catch (Exception ex) {
                        // noop
                    } finally {
                        eventLedger.unregisterListener(listener);
                    }
                });
    }

    private Map<String, Object> makeStep(String id, String type, String status, String title, String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("status", status);
        m.put("title", title);
        m.put("description", description);
        m.put("startedAt", Instant.now().toString());
        return m;
    }
}
