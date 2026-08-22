package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.wayang.pro.api.grpc.*;
import tech.kayys.aljabr.api.entity.ProjectEntity;
import tech.kayys.aljabr.api.entity.SessionEntity;
import tech.kayys.aljabr.api.entity.ChatMessageEntity;
import tech.kayys.aljabr.api.repository.ProjectRepository;
import tech.kayys.aljabr.api.repository.SessionRepository;
import tech.kayys.aljabr.engine.index.indexer.WorkspaceIndexer;

import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@GrpcService
@jakarta.inject.Singleton
public class ProjectServiceImpl extends MutinyProjectServiceGrpc.ProjectServiceImplBase implements ProjectService {

    private static final Logger log = Logger.getLogger(ProjectServiceImpl.class.getName());

    @Inject
    ProjectRepository projectRepo;

    @Inject
    SessionRepository sessionRepo;

    @Inject
    tech.kayys.aljabr.api.repository.ChatMessageRepository chatMessageRepo;

    @Inject
    WorkspaceIndexer workspaceIndexer;

    // Resilient in-memory fallback cache when PostgreSQL is offline or in standalone mode
    private final Map<String, ProjectEntity> memoryProjects = new ConcurrentHashMap<>();
    private final Map<String, SessionEntity> memorySessions = new ConcurrentHashMap<>();

    public ProjectServiceImpl() {
        // Pre-seed default local workspace using WORKSPACE_ROOT env or user.dir
        String workspaceRoot = System.getenv("WORKSPACE_ROOT");
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            workspaceRoot = System.getProperty("workspace.root");
        }
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            workspaceRoot = System.getProperty("user.dir");
        }

        ProjectEntity defaultProject = new ProjectEntity();
        defaultProject.id = "local-workspace";
        defaultProject.name = "Aljabr Workspace";
        defaultProject.description = "Primary Local Workspace";
        defaultProject.basePath = workspaceRoot != null ? workspaceRoot : "/tmp";
        defaultProject.archived = false;
        memoryProjects.put(defaultProject.id, defaultProject);

        SessionEntity defaultSession = new SessionEntity();
        defaultSession.id = "default-session";
        defaultSession.projectId = defaultProject.id;
        defaultSession.name = "General Agent Session";
        defaultSession.status = "ACTIVE";
        memorySessions.put(defaultSession.id, defaultSession);
    }

    @Override
    public Uni<Project> createProject(CreateProjectRequest request) {
        ProjectEntity p = new ProjectEntity();
        p.id = UUID.randomUUID().toString();
        p.name = request.getName() != null && !request.getName().isBlank() ? request.getName() : "New Project";
        p.description = request.getDescription() != null ? request.getDescription() : "";
        p.basePath = request.getBasePath() != null ? request.getBasePath() : ".";
        p.archived = false;

        memoryProjects.put(p.id, p);
        return Uni.createFrom().item(toProjectProto(p));
    }

    @Override
    public Uni<ListProjectsResponse> listProjects(ListProjectsRequest request) {
        List<Project> projects = memoryProjects.values().stream()
            .filter(p -> request.getIncludeArchived() || !Boolean.TRUE.equals(p.archived))
            .map(this::toProjectProto)
            .collect(Collectors.toList());
        return Uni.createFrom().item(ListProjectsResponse.newBuilder().addAllProjects(projects).build());
    }

    @Override
    public Uni<Session> createSession(CreateSessionRequest request) {
        SessionEntity s = new SessionEntity();
        s.id = UUID.randomUUID().toString();
        s.projectId = request.getProjectId() != null && !request.getProjectId().isBlank() ? request.getProjectId() : "local-workspace";
        s.name = request.getName() != null && !request.getName().isBlank() ? request.getName() : "New Chat";
        s.status = "ACTIVE";

        memorySessions.put(s.id, s);
        return Uni.createFrom().item(toSessionProto(s));
    }

    @Override
    public Uni<ListSessionsResponse> listSessions(ListSessionsRequest request) {
        final String targetProj = request.getProjectId();
        List<Session> sessions = memorySessions.values().stream()
            .filter(s -> targetProj == null || targetProj.isEmpty() || targetProj.equals(s.projectId))
            .map(this::toSessionProto)
            .collect(Collectors.toList());
        return Uni.createFrom().item(ListSessionsResponse.newBuilder().addAllSessions(sessions).build());
    }

    @Override
    public Uni<Session> forkSession(ForkSessionRequest request) {
        SessionEntity orig = memorySessions.get(request.getSessionId());
        SessionEntity s = new SessionEntity();
        s.id = UUID.randomUUID().toString();
        s.projectId = orig != null ? orig.projectId : "local-workspace";
        s.name = (orig != null ? orig.name : "Session") + " (Fork)";
        s.status = "ACTIVE";

        memorySessions.put(s.id, s);
        return Uni.createFrom().item(toSessionProto(s));
    }

    @Override
    public Uni<DeleteSessionResponse> deleteSession(DeleteSessionRequest request) {
        if (request.getSessionId() != null) {
            memorySessions.remove(request.getSessionId());
        }
        return Uni.createFrom().item(DeleteSessionResponse.newBuilder().setSuccess(true).build());
    }

    @Override
    public Uni<IndexWorkspaceResponse> indexWorkspace(IndexWorkspaceRequest request) {
        String projectId = request.getProjectId();
        String workspacePathStr = request.getWorkspacePath();

        Uni<Path> rootUni;
        if (workspacePathStr != null && !workspacePathStr.isBlank()) {
            rootUni = Uni.createFrom().item(Path.of(workspacePathStr));
        } else if (projectId != null && !projectId.isBlank() && memoryProjects.containsKey(projectId)) {
            ProjectEntity p = memoryProjects.get(projectId);
            rootUni = Uni.createFrom().item(Path.of(p.basePath != null ? p.basePath : "."));
        } else {
            rootUni = Uni.createFrom().item(Path.of("."));
        }

        return rootUni.flatMap(root -> {
            if (workspaceIndexer != null) {
                return workspaceIndexer.index(root, projectId != null ? projectId : "default")
                    .map(res -> IndexWorkspaceResponse.newBuilder()
                        .setSuccess(true)
                        .setFilesIndexed(res.filesIndexed())
                        .setSymbolsIndexed(res.symbolsIndexed())
                        .setDependenciesIndexed(res.dependenciesIndexed())
                        .setDurationMs(res.durationMs())
                        .setRepositoryHash(res.fingerprint() != null && res.fingerprint().repositoryHash() != null ? res.fingerprint().repositoryHash() : "")
                        .build());
            } else {
                return Uni.createFrom().item(IndexWorkspaceResponse.newBuilder()
                    .setSuccess(false)
                    .setError("WorkspaceIndexer not available")
                    .build());
            }
        }).onFailure().recoverWithItem(err -> IndexWorkspaceResponse.newBuilder()
            .setSuccess(false)
            .setError(err.getMessage() != null ? err.getMessage() : "Unknown error during indexing")
            .build());
    }

    @Override
    public Uni<Session> updateSessionStatus(UpdateSessionStatusRequest request) {
        SessionEntity s = memorySessions.get(request.getSessionId());
        if (s != null) {
            s.status = request.getStatus();
        }
        return Uni.createFrom().item(s != null ? toSessionProto(s) : Session.newBuilder().setId(request.getSessionId()).setStatus(request.getStatus()).build());
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
