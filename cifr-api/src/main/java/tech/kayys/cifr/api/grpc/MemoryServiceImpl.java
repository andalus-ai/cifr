package tech.kayys.cifr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.cifr.api.memory.MemoryExportService;
import tech.kayys.andalus.memory.MemorySnapshot;
import tech.kayys.andalus.pro.api.grpc.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@GrpcService
public class MemoryServiceImpl implements MemoryService {

    private static final String DEFAULT_BACKUP_DIR = System.getProperty("user.home") + "/.cifr/memory-backups";

    @Inject
    MemoryExportService exportService;

    @Override
    public Uni<ExportMemoryResponse> exportMemory(ExportMemoryRequest request) {
        return exportService.exportSnapshot(request.getAgentId(), request.getUserId(), request.getTenantId())
                .map(snapshot -> {
                    String json = "";
                    try {
                        json = new String(exportService.serializeSnapshot(snapshot));
                    } catch (Exception ignored) {}

                    return ExportMemoryResponse.newBuilder()
                            .setSnapshotId(snapshot.snapshotId())
                            .setAgentId(snapshot.agentId())
                            .setRecordCount(snapshot.records().size())
                            .addAllTiers(snapshot.tiers())
                            .setSnapshotJson(json)
                            .setCapturedAt(snapshot.capturedAt().toString())
                            .build();
                });
    }

    @Override
    public Uni<ImportMemoryResponse> importMemory(ImportMemoryRequest request) {
        return Uni.createFrom().item(() -> {
            try {
                return exportService.deserializeSnapshot(request.getSnapshotJson());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid snapshot JSON: " + e.getMessage());
            }
        }).flatMap(snapshot -> exportService.importSnapshot(snapshot, request.getOverwrite())
                .map(result -> ImportMemoryResponse.newBuilder()
                        .setSuccess(true)
                        .setSnapshotId(result.snapshotId())
                        .setAgentId(result.agentId())
                        .setTotalRecords(result.totalRecords())
                        .setWrittenRecords(result.writtenRecords())
                        .setSkippedRecords(result.skippedRecords())
                        .setImportedAt(result.importedAt().toString())
                        .build()));
    }

    @Override
    public Uni<BackupMemoryResponse> backupMemory(BackupMemoryRequest request) {
        Path backupDir = request.getDestinationPath().isBlank()
                ? Paths.get(DEFAULT_BACKUP_DIR)
                : Paths.get(request.getDestinationPath());

        return exportService.exportSnapshot(request.getAgentId(), request.getUserId(), request.getTenantId())
                .flatMap(snapshot -> Uni.createFrom().item(() -> {
                    try {
                        Path writtenFile = exportService.backupToDisk(snapshot, backupDir);
                        return BackupMemoryResponse.newBuilder()
                                .setSuccess(true)
                                .setSnapshotId(snapshot.snapshotId())
                                .setAgentId(request.getAgentId())
                                .setRecordCount(snapshot.records().size())
                                .setFilePath(writtenFile.toAbsolutePath().toString())
                                .setCapturedAt(snapshot.capturedAt().toString())
                                .build();
                    } catch (Exception e) {
                        throw new RuntimeException("Backup failed: " + e.getMessage(), e);
                    }
                }));
    }
}
