package tech.kayys.aljabr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.*;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class ArchiveTool implements AljabrTool {
    
    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_ARCHIVE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "archive",
            "Create, extract, or list zip archive files within the workspace.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of(
                        "type", "string", 
                        "enum", List.of("create", "extract", "list"),
                        "description", "Operation to perform"
                    ),
                    "source", Map.of(
                        "type", "array",
                        "description", "Source path(s) to archive or archive file to extract",
                        "items", Map.of("type", "string")
                    ),
                    "destination", Map.of(
                        "type", "string",
                        "description", "Archive path (for create) or extraction target (for extract)"
                    )
                ),
                "required", List.of("action", "source")
            ),
            Set.of(ToolCapability.READ_ONLY, ToolCapability.WRITE),
            ToolRisk.MEDIUM
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String action = (String) request.arguments().get("action");
                List<String> sources = (List<String>) request.arguments().get("source");
                String destination = (String) request.arguments().get("destination");

                if (sources == null || sources.isEmpty()) {
                    return new ArchiveResult(false, "No source paths specified", null, 0, 0);
                }

                List<Path> resolvedSources = new ArrayList<>();
                for (String source : sources) {
                    Path resolved = WORKSPACE_ROOT.resolve(source).normalize();
                    if (!resolved.startsWith(WORKSPACE_ROOT)) {
                        return new ArchiveResult(false, "Access denied: path outside workspace: " + source, null, 0, 0);
                    }
                    if (!Files.exists(resolved)) {
                        return new ArchiveResult(false, "Source does not exist: " + source, null, 0, 0);
                    }
                    resolvedSources.add(resolved);
                }

                Path destPath = destination != null ? WORKSPACE_ROOT.resolve(destination).normalize() : null;
                if (destPath != null && !destPath.startsWith(WORKSPACE_ROOT)) {
                    return new ArchiveResult(false, "Access denied: destination outside workspace", null, 0, 0);
                }

                return switch (action.toLowerCase()) {
                    case "create" -> createZipArchive(resolvedSources, destPath);
                    case "extract" -> extractZipArchive(resolvedSources.get(0), destPath);
                    case "list" -> listZipArchive(resolvedSources.get(0));
                    default -> new ArchiveResult(false, "Unknown action: " + action, null, 0, 0);
                };

            } catch (Exception e) {
                return new ArchiveResult(false, "Archive operation failed: " + e.getMessage(), null, 0, 0);
            }
        });
    }

    private ArchiveResult createZipArchive(List<Path> sources, Path destPath) {
        try {
            if (destPath == null) {
                String baseName = sources.get(0).getFileName().toString();
                destPath = WORKSPACE_ROOT.resolve(baseName + ".zip");
            }

            if (destPath.getParent() != null) {
                Files.createDirectories(destPath.getParent());
            }

            List<Path> allFiles = new ArrayList<>();
            Map<Path, Path> sourceMapping = new LinkedHashMap<>();

            for (Path source : sources) {
                if (Files.isDirectory(source)) {
                    try (var walk = Files.walk(source)) {
                        walk.filter(Files::isRegularFile).forEach(file -> {
                            allFiles.add(file);
                            sourceMapping.put(file, source);
                        });
                    }
                } else {
                    allFiles.add(source);
                    sourceMapping.put(source, source);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(destPath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos, BUFFER_SIZE))) {
                zos.setLevel(6);
                zos.setMethod(ZipOutputStream.DEFLATED);
                zos.setComment("Created by Aljabr on " + Instant.now());

                byte[] buffer = new byte[BUFFER_SIZE];
                for (Path file : allFiles) {
                    Path source = sourceMapping.get(file);
                    String entryName = source.relativize(file).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(Files.getLastModifiedTime(file).toMillis());
                    entry.setSize(Files.size(file));

                    zos.putNextEntry(entry);
                    try (InputStream is = Files.newInputStream(file)) {
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                    }
                    zos.closeEntry();
                }
            }

            long size = Files.size(destPath);
            return new ArchiveResult(true, "Archive created successfully: " + destPath, destPath.toString(), size, allFiles.size());

        } catch (Exception e) {
            return new ArchiveResult(false, "Failed to create archive: " + e.getMessage(), null, 0, 0);
        }
    }

    private ArchiveResult extractZipArchive(Path archivePath, Path destPath) {
        try {
            if (destPath == null) {
                destPath = WORKSPACE_ROOT.resolve(archivePath.getFileName().toString().replaceAll("\\.[^.]+$", ""));
            }
            Files.createDirectories(destPath);

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archivePath), BUFFER_SIZE))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        Files.createDirectories(destPath.resolve(entry.getName()));
                        continue;
                    }
                    Path target = destPath.resolve(entry.getName()).normalize();
                    if (!target.startsWith(destPath)) {
                        throw new SecurityException("Zip entry attempts path traversal: " + entry.getName());
                    }
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    zis.closeEntry();
                }
            }

            long totalFiles;
            long totalSize;
            try (var walk = Files.walk(destPath)) {
                List<Path> files = walk.filter(Files::isRegularFile).toList();
                totalFiles = files.size();
                totalSize = files.stream().mapToLong(p -> {
                    try { return Files.size(p); } catch (IOException e) { return 0; }
                }).sum();
            }

            return new ArchiveResult(true, "Extracted " + totalFiles + " files to: " + destPath, destPath.toString(), totalSize, (int) totalFiles);

        } catch (Exception e) {
            return new ArchiveResult(false, "Failed to extract archive: " + e.getMessage(), null, 0, 0);
        }
    }

    private ArchiveResult listZipArchive(Path archivePath) {
        try {
            List<ArchiveEntryInfo> entries = new ArrayList<>();
            try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    entries.add(new ArchiveEntryInfo(
                        entry.getName(),
                        entry.isDirectory(),
                        entry.getSize(),
                        entry.getTime()
                    ));
                }
            }

            long totalSize = entries.stream().mapToLong(ArchiveEntryInfo::size).sum();
            return new ArchiveResult(true, "Archive contains " + entries.size() + " entries",
                archivePath.toString(), totalSize, entries.size(), entries);

        } catch (Exception e) {
            return new ArchiveResult(false, "Failed to list archive: " + e.getMessage(), null, 0, 0);
        }
    }
}
