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
        final long now = Instant.now().toEpochMilli();

        // build plan steps
        Map<String, Object>[] steps = new Map[5];
        steps[0] = makeStep("s1", "analysis", "pending", "Analyze repository", "Scanning for auth flow");
        steps[1] = makeStep("s2", "fileRead", "pending", "Read SessionService.java", "Open file to inspect auth logic");
        steps[2] = makeStep("s3", "fileWrite", "pending", "Modify SessionService.java", "Make session lookup null-safe");
        steps[3] = makeStep("s4", "test", "pending", "Run tests", "Execute unit test suite");
        steps[4] = makeStep("s5", "verification", "pending", "Verify changes", "Check that failing tests are resolved");

        // send planning snapshot immediately
        for (int i = 0; i < steps.length; i++) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("event", "step");
            ev.put("data", steps[i]);
            sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(ev)).build());
        }

        // schedule running/completion events
        int delay = 500;
        for (int i = 0; i < steps.length; i++) {
            final int idx = i;
            // running
            exec.schedule(() -> {
                Map<String, Object> running = new HashMap<>(steps[idx]);
                running.put("status", "running");
                Map<String, Object> ev = new HashMap<>();
                ev.put("event", "step");
                ev.put("data", running);
                sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(ev)).build());
            }, delay, TimeUnit.MILLISECONDS);
            delay += 800;

            // completed/failure
            exec.schedule(() -> {
                Map<String, Object> completed = new HashMap<>(steps[idx]);
                String type = (String) completed.get("type");
                completed.put("status", "completed");
                if ("test".equals(type)) completed.put("status", "failed");
                completed.put("completedAt", Instant.now().toString());
                completed.put("durationMs", 1000);
                Map<String, Object> ev = new HashMap<>();
                ev.put("event", "step");
                ev.put("data", completed);
                sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(ev)).build());

                // if test failed, emit inspect/retry events
                if ("test".equals(type)) {
                    Map<String, Object> inspect = makeStep(type + "-inspect", "analysis", "pending", "Inspect failures", "Analyze failing tests");
                    Map<String, Object> ev2 = new HashMap<>();
                    ev2.put("event", "step");
                    ev2.put("data", inspect);
                    sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(ev2)).build());

                    Map<String, Object> inspectDone = new HashMap<>(inspect);
                    inspectDone.put("status", "completed");
                    inspectDone.put("completedAt", Instant.now().toString());
                    exec.schedule(() -> sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(Map.of("event","step","data",inspectDone))).build()), 300, TimeUnit.MILLISECONDS);

                    Map<String, Object> retry = makeStep(type + "-retry", "fileWrite", "running", "Adjust implementation", "Fix failing assertion");
                    exec.schedule(() -> sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(Map.of("event","step","data",retry))).build()), 400, TimeUnit.MILLISECONDS);
                    Map<String, Object> retryDone = new HashMap<>(retry);
                    retryDone.put("status", "completed");
                    retryDone.put("completedAt", Instant.now().toString());
                    exec.schedule(() -> sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(Map.of("event","step","data",retryDone))).build()), 1000, TimeUnit.MILLISECONDS);

                    Map<String, Object> rerun = makeStep(type + "-rerun", "test", "running", "Run tests (iteration 2)", "Run test suite again");
                    exec.schedule(() -> sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(Map.of("event","step","data",rerun))).build()), 1100, TimeUnit.MILLISECONDS);
                    Map<String, Object> rerunDone = new HashMap<>(rerun);
                    rerunDone.put("status", "completed");
                    rerunDone.put("completedAt", Instant.now().toString());
                    exec.schedule(() -> sink.send(sse.newEventBuilder().name("step").data(String.class, toJson(Map.of("event","step","data",rerunDone))).build()), 1900, TimeUnit.MILLISECONDS);
                }
            }, delay, TimeUnit.MILLISECONDS);

            delay += 1200;
        }

        // final done event
        exec.schedule(() -> {
            if (sink.isClosed()) return;
            sink.send(sse.newEventBuilder().name("done").data(String.class, toJson(Map.of("event","done"))).build());
            sink.close();
        }, delay + 200, TimeUnit.MILLISECONDS);
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
