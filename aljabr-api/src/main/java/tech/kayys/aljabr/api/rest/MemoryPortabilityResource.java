package tech.kayys.aljabr.api.rest;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.aljabr.api.memory.MemoryExportService;
import tech.kayys.andalus.memory.MemorySnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * REST API for portable memory export, import, and backup.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/memory/export/{agentId}} — download a full JSON snapshot</li>
 *   <li>{@code POST /api/v1/memory/import/{agentId}} — restore memory from a JSON snapshot</li>
 *   <li>{@code POST /api/v1/memory/backup/{agentId}} — write snapshot to disk (server-side backup)</li>
 *   <li>{@code GET  /api/v1/memory/backups/{agentId}} — list available on-disk backups</li>
 * </ul>
 */
@Path("/api/v1/memory")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MemoryPortabilityResource {

    private static final String DEFAULT_BACKUP_DIR = System.getProperty("user.home") + "/.aljabr/memory-backups";

    @Inject
    MemoryExportService exportService;

    /**
     * Export all memory for the given agent as a downloadable JSON snapshot.
     */
    @GET
    @Path("/export/{agentId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> exportMemory(
            @PathParam("agentId") String agentId,
            @QueryParam("userId") String userId,
            @QueryParam("tenantId") String tenantId) {

        return exportService.exportSnapshot(agentId, userId, tenantId)
                .map(snapshot -> {
                    try {
                        byte[] json = exportService.serializeSnapshot(snapshot);
                        String filename = "memory-snapshot-" + agentId + "-"
                                + snapshot.capturedAt().toEpochMilli() + ".json";
                        return Response.ok(json, MediaType.APPLICATION_JSON)
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .header("X-Snapshot-Id", snapshot.snapshotId())
                                .header("X-Record-Count", snapshot.records().size())
                                .build();
                    } catch (Exception e) {
                        return Response.serverError()
                                .entity(Map.of("error", "Serialization failed: " + e.getMessage()))
                                .build();
                    }
                });
    }

    /**
     * Import (restore) memory from a JSON snapshot uploaded in the request body.
     */
    @POST
    @Path("/import/{agentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> importMemory(
            @PathParam("agentId") String agentId,
            @QueryParam("overwrite") @DefaultValue("false") boolean overwrite,
            String body) {

        return Uni.createFrom().item(() -> {
            try {
                MemorySnapshot snapshot = exportService.deserializeSnapshot(body);

                if (snapshot.agentId() != null && !snapshot.agentId().equals(agentId)) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Snapshot agentId '" + snapshot.agentId()
                                    + "' does not match path agentId '" + agentId + "'"))
                            .build();
                }
                return snapshot;
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid snapshot JSON: " + e.getMessage()))
                        .build();
            }
        }).flatMap(obj -> {
            if (obj instanceof Response r) return Uni.createFrom().item(r);
            MemorySnapshot snapshot = (MemorySnapshot) obj;
            return exportService.importSnapshot(snapshot, overwrite)
                    .map(result -> Response.ok(Map.of(
                            "success", true,
                            "snapshotId", result.snapshotId(),
                            "agentId", result.agentId(),
                            "totalRecords", result.totalRecords(),
                            "writtenRecords", result.writtenRecords(),
                            "skippedRecords", result.skippedRecords(),
                            "countsByTier", result.countsByTier(),
                            "importedAt", result.importedAt().toString()
                    )).build());
        });
    }

    /**
     * Trigger a server-side backup — writes a snapshot to disk.
     */
    @POST
    @Path("/backup/{agentId}")
    public Uni<Response> backupMemory(
            @PathParam("agentId") String agentId,
            @QueryParam("userId") String userId,
            @QueryParam("tenantId") String tenantId,
            @QueryParam("path") String customPath) {

        java.nio.file.Path backupDir = customPath != null ? Paths.get(customPath) : Paths.get(DEFAULT_BACKUP_DIR);

        return exportService.exportSnapshot(agentId, userId, tenantId)
                .flatMap(snapshot -> Uni.createFrom().item(() -> {
                    try {
                        java.nio.file.Path file = exportService.backupToDisk(snapshot, backupDir);
                        return Response.ok(Map.of(
                                "success", true,
                                "snapshotId", snapshot.snapshotId(),
                                "agentId", agentId,
                                "recordCount", snapshot.records().size(),
                                "tiers", snapshot.tiers(),
                                "filePath", file.toAbsolutePath().toString(),
                                "capturedAt", snapshot.capturedAt().toString()
                        )).build();
                    } catch (IOException e) {
                        return Response.serverError()
                                .entity(Map.of("error", "Backup failed: " + e.getMessage()))
                                .build();
                    }
                }));
    }

    /**
     * List available on-disk backup snapshots for a given agent.
     */
    @GET
    @Path("/backups/{agentId}")
    public Uni<Response> listBackups(
            @PathParam("agentId") String agentId,
            @QueryParam("path") String customPath) {

        return Uni.createFrom().item(() -> {
            java.nio.file.Path backupDir = customPath != null ? Paths.get(customPath) : Paths.get(DEFAULT_BACKUP_DIR);
            try {
                if (!Files.exists(backupDir)) {
                    return Response.ok(Map.of("backups", java.util.List.of())).build();
                }
                var backups = Files.list(backupDir)
                        .filter(f -> f.getFileName().toString().startsWith("memory-snapshot-" + agentId))
                        .sorted(java.util.Comparator.comparing(f -> {
                            try { return Files.getLastModifiedTime(f).toInstant(); }
                            catch (IOException e) { return Instant.EPOCH; }
                        }))
                        .map(f -> Map.of(
                                "fileName", f.getFileName().toString(),
                                "path", f.toAbsolutePath().toString(),
                                "sizeBytes", f.toFile().length()
                        ))
                        .toList();
                return Response.ok(Map.of(
                        "agentId", agentId,
                        "backupDir", backupDir.toAbsolutePath().toString(),
                        "backups", backups
                )).build();
            } catch (IOException e) {
                return Response.serverError()
                        .entity(Map.of("error", "Failed to list backups: " + e.getMessage()))
                        .build();
            }
        });
    }
}
