package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import tech.kayys.wayang.pro.api.grpc.*;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@GrpcService
public class ProjectServiceImpl implements ProjectService {

    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Uni<Project> createProject(CreateProjectRequest request) {
        Project p = Project.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setName(request.getName())
                .setDescription(request.getDescription())
                .setBasePath(request.getBasePath())
                .build();
        projects.put(p.getId(), p);
        return Uni.createFrom().item(p);
    }

    @Override
    public Uni<ListProjectsResponse> listProjects(ListProjectsRequest request) {
        return Uni.createFrom().item(ListProjectsResponse.newBuilder()
                .addAllProjects(projects.values())
                .build());
    }

    @Override
    public Uni<Session> createSession(CreateSessionRequest request) {
        Session s = Session.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setProjectId(request.getProjectId())
                .setName(request.getName())
                .setStatus("QUEUED")
                .build();
        sessions.put(s.getId(), s);
        return Uni.createFrom().item(s);
    }

    @Override
    public Uni<ListSessionsResponse> listSessions(ListSessionsRequest request) {
        List<Session> result = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (s.getProjectId().equals(request.getProjectId())) {
                result.add(s);
            }
        }
        return Uni.createFrom().item(ListSessionsResponse.newBuilder()
                .addAllSessions(result)
                .build());
    }

    @Override
    public Uni<Session> forkSession(ForkSessionRequest request) {
        Session orig = sessions.get(request.getSessionId());
        if (orig == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Session not found"));
        }
        Session s = Session.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setProjectId(orig.getProjectId())
                .setName(orig.getName() + " (Fork)")
                .setStatus("QUEUED")
                .build();
        sessions.put(s.getId(), s);
        return Uni.createFrom().item(s);
    }

    @Override
    public Uni<Session> updateSessionStatus(UpdateSessionStatusRequest request) {
        Session orig = sessions.get(request.getSessionId());
        if (orig == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Session not found"));
        }
        Session updated = orig.toBuilder().setStatus(request.getStatus()).build();
        sessions.put(updated.getId(), updated);
        return Uni.createFrom().item(updated);
    }
}
