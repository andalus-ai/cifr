package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CopyTool implements CifrTool {


    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "copy",
            "Copy a file or directory to a new location in the workspace.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "source", Map.of("type", "string", "description", "Source path"),
                    "destination", Map.of("type", "string", "description", "Destination path"),
                    "recursive", Map.of("type", "boolean", "description", "Copy directories recursively"),
                    "overwrite", Map.of("type", "boolean", "description", "Overwrite if destination exists")
                ),
                "required", List.of("source", "destination")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_WORKSPACE),
            ToolRisk.MEDIUM
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String srcStr = (String) request.arguments().get("source");
                String dstStr = (String) request.arguments().get("destination");
                boolean recursive = Boolean.TRUE.equals(request.arguments().get("recursive"));
                boolean overwrite = Boolean.TRUE.equals(request.arguments().get("overwrite"));

                if (srcStr == null || dstStr == null) return new CopyResult(false, "Source and destination required");

                Path src = WorkspaceResolver.resolveWorkspacePath(context, srcStr);
                Path dst = WorkspaceResolver.resolveWorkspacePath(context, dstStr);

                if (!Files.exists(src))
                    return new CopyResult(false, "Source does not exist: " + srcStr);
                if (Files.exists(dst) && !overwrite)
                    return new CopyResult(false, "Destination already exists: " + dstStr);

                if (dst.getParent() != null) Files.createDirectories(dst.getParent());

                CopyOption[] options = overwrite ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[]{};

                if (Files.isDirectory(src)) {
                    if (!recursive) return new CopyResult(false, "Directory requires recursive=true");
                    Files.walk(src).forEach(source -> {
                        Path dest = dst.resolve(src.relativize(source));
                        try {
                            if (Files.isDirectory(source)) Files.createDirectories(dest);
                            else Files.copy(source, dest, options);
                        } catch (IOException e) { throw new RuntimeException(e); }
                    });
                    return new CopyResult(true, "Directory copied: " + srcStr + " -> " + dstStr, true);
                } else {
                    Files.copy(src, dst, options);
                    return new CopyResult(true, "File copied: " + srcStr + " -> " + dstStr, false);
                }
            } catch (IOException e) {
                return new CopyResult(false, "IO error: " + e.getMessage());
            }
        });
    }
}
