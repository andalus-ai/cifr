package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import tech.kayys.wayang.pro.api.grpc.*;
import tech.kayys.aljabr.api.entity.ProjectEntity;
import tech.kayys.aljabr.api.entity.SessionEntity;
import tech.kayys.aljabr.api.repository.ProjectRepository;
import tech.kayys.aljabr.api.repository.SessionRepository;

import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@GrpcService
public class ProjectServiceImpl implements ProjectService {

    @Inject
    ProjectRepository projectRepo;

    @Inject
    SessionRepository sessionRepo;

    @Override
    @WithTransaction
    public Uni<Project> createProject(CreateProjectRequest request) {
        ProjectEntity p = new ProjectEntity();
        p.id = UUID.randomUUID().toString();
        p.name = request.getName();
        p.description = request.getDescription();
        p.basePath = request.getBasePath();
        p.archived = false;

        return projectRepo.persist(p).map(this::toProjectProto);
    }

    @Override
    public Uni<ListProjectsResponse> listProjects(ListProjectsRequest request) {
        return projectRepo.listAll().map(entities -> {
            List<Project> projects = entities.stream()
                .map(this::toProjectProto)
                .collect(Collectors.toList());
            return ListProjectsResponse.newBuilder()
                    .addAllProjects(projects)
                    .build();
        });
    }

    @Override
    @WithTransaction
    public Uni<Session> createSession(CreateSessionRequest request) {
        SessionEntity s = new SessionEntity();
        s.id = UUID.randomUUID().toString();
        s.projectId = request.getProjectId();
        s.name = request.getName();
        s.status = "QUEUED";

        return sessionRepo.persist(s).map(this::toSessionProto);
    }

    @Override
    public Uni<ListSessionsResponse> listSessions(ListSessionsRequest request) {
        return sessionRepo.find("projectId", request.getProjectId()).list().map(entities -> {
            List<Session> sessions = entities.stream()
                .map(this::toSessionProto)
                .collect(Collectors.toList());
            return ListSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build();
        });
    }

    @Override
    @WithTransaction
    public Uni<Session> forkSession(ForkSessionRequest request) {
        return sessionRepo.findById(request.getSessionId())
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Session not found"))
            .flatMap(orig -> {
                SessionEntity s = new SessionEntity();
                s.id = UUID.randomUUID().toString();
                s.projectId = orig.projectId;
                s.name = orig.name + " (Fork)";
                s.status = "QUEUED";
                return sessionRepo.persist(s);
            })
            .map(this::toSessionProto);
    }

    @Override
    @WithTransaction
    public Uni<Session> updateSessionStatus(UpdateSessionStatusRequest request) {
        return sessionRepo.findById(request.getSessionId())
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Session not found"))
            .flatMap(orig -> {
                orig.status = request.getStatus();
                return sessionRepo.persist(orig);
            })
            .map(this::toSessionProto);
    }

    private Project toProjectProto(ProjectEntity p) {
        return Project.newBuilder()
                .setId(p.id != null ? p.id : "")
                .setName(p.name != null ? p.name : "")
                .setDescription(p.description != null ? p.description : "")
                .setBasePath(p.basePath != null ? p.basePath : "")
                .build();
    }

    private Session toSessionProto(SessionEntity s) {
        return Session.newBuilder()
                .setId(s.id != null ? s.id : "")
                .setProjectId(s.projectId != null ? s.projectId : "")
                .setName(s.name != null ? s.name : "")
                .setStatus(s.status != null ? s.status : "")
                .build();
    }
}
