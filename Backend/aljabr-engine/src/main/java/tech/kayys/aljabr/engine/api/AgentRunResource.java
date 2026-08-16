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

    private String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return '"' + ((String) o).replace("\"", "\\\"") + '"';
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map) {
            Map m = (Map) o;
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Object entryObj : m.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObj;
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (o instanceof List) {
            List l = (List) o;
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Object item : l) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return '"' + o.toString().replace("\"", "\\\"") + '"';
    }

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
                out.put("data", ev);
                sink.send(sse.newEventBuilder().name("agent-event").data(String.class, toJson(out)).build());
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
                .parameters(params)
                .build();

        // Run orchestrator asynchronously and close stream when done
        orchestrator.execute(agentRequest)
                .subscribe().with(response -> {
                    try {
                        if (sink == null || sink.isClosed()) return;
                        sink.send(sse.newEventBuilder().name("done").data(String.class, toJson(Map.of("event","done","result", response.metadata()))).build());
                        sink.close();
                    } finally {
                        eventLedger.unregisterListener(listener);
                    }
                }, err -> {
                    try {
                        if (sink == null || sink.isClosed()) return;
                        sink.send(sse.newEventBuilder().name("error").data(String.class, toJson(Map.of("event","error","message", err.getMessage()))).build());
                        sink.close();
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
