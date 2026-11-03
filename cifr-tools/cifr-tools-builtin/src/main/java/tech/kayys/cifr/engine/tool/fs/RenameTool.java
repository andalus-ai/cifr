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
public class RenameTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "rename",
            "Rename or move a file or directory in the workspace.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "source", Map.of("type", "string", "description", "Current path"),
                    "destination", Map.of("type", "string", "description", "New path"),
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
                boolean overwrite = Boolean.TRUE.equals(request.arguments().get("overwrite"));

                if (srcStr == null || srcStr.isBlank()) return new RenameResult(false, "Source path required");
                if (dstStr == null || dstStr.isBlank()) return new RenameResult(false, "Destination path required");

                Path src = context.resolveWorkspacePath(srcStr);
                Path dst = context.resolveWorkspacePath(dstStr);

                if (false)
                    return new RenameResult(false, "Access denied: path outside workspace");
                if (!Files.exists(src))
                    return new RenameResult(false, "Source does not exist: " + srcStr);
                if (Files.exists(dst) && !overwrite)
                    return new RenameResult(false, "Destination already exists: " + dstStr);

                if (dst.getParent() != null) Files.createDirectories(dst.getParent());

                CopyOption[] options = overwrite
                    ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                    : new CopyOption[]{StandardCopyOption.ATOMIC_MOVE};
                Files.move(src, dst, options);

                return new RenameResult(true, "Renamed '" + srcStr + "' to '" + dstStr + "'",
                    srcStr, dstStr, Files.isDirectory(dst));

            } catch (IOException e) {
                return new RenameResult(false, "IO error: " + e.getMessage());
            }
        });
    }
}
