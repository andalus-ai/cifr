package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
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
import java.util.stream.Collectors;

@GrpcService
public class ProjectServiceImpl implements ProjectService {

    @Inject
    ProjectRepository projectRepo;

    @Inject
    SessionRepository sessionRepo;

    @Inject
    tech.kayys.aljabr.api.repository.ChatMessageRepository chatMessageRepo;

    @Inject
    WorkspaceIndexer workspaceIndexer;

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
                return sessionRepo.persist(s).flatMap(persistedSession -> {
                    return chatMessageRepo.find("sessionId", request.getSessionId()).list()
                        .flatMap(msgs -> {
                            if (msgs.isEmpty()) {
                                return Uni.createFrom().item(persistedSession);
                            }
                            List<ChatMessageEntity> toCopy = new java.util.ArrayList<>();
                            for (ChatMessageEntity msg : msgs) {
                                ChatMessageEntity copy = new ChatMessageEntity();
                                copy.id = UUID.randomUUID().toString();
                                copy.sessionId = persistedSession.id;
                                copy.tenantId = msg.tenantId;
                                copy.role = msg.role;
                                copy.content = msg.content;
                                copy.toolCalls = msg.toolCalls;
                                copy.tokens = msg.tokens;
                                copy.model = msg.model;
                                copy.createdAt = msg.createdAt;
                                toCopy.add(copy);
                                if (request.getMessageId() != null && !request.getMessageId().isEmpty() && request.getMessageId().equals(msg.id)) {
                                    break;
                                }
                            }
                            return chatMessageRepo.persist(toCopy).map(v -> persistedSession);
                        });
                });
            })
            .map(this::toSessionProto);
    }

    @Override
    @WithTransaction
    public Uni<DeleteSessionResponse> deleteSession(DeleteSessionRequest request) {
        return chatMessageRepo.delete("sessionId", request.getSessionId())
            .flatMap(ignored -> sessionRepo.deleteById(request.getSessionId()))
            .map(deleted -> DeleteSessionResponse.newBuilder().setSuccess(deleted).build());
    }

    @Override
    public Uni<IndexWorkspaceResponse> indexWorkspace(IndexWorkspaceRequest request) {
        String projectId = request.getProjectId();
        String workspacePathStr = request.getWorkspacePath();

        Uni<Path> rootUni;
        if (workspacePathStr != null && !workspacePathStr.isBlank()) {
            rootUni = Uni.createFrom().item(Path.of(workspacePathStr));
        } else if (projectId != null && !projectId.isBlank()) {
            rootUni = projectRepo.findById(projectId).map(p -> {
                if (p != null && p.basePath != null && !p.basePath.isBlank()) {
                    return Path.of(p.basePath);
                }
                return Path.of(".");
            });
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
