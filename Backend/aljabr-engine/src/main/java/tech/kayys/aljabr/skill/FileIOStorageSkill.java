package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

@ApplicationScoped
public class FileIOStorageSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("file_path", "Absolute path to the file");
    }


    private static final Logger LOG = Logger.getLogger(FileIOStorageSkill.class);

    

    

    

    

    

        

    @jakarta.inject.Inject
    tech.kayys.muqabalah.api.CodeTool codeTool;

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String filePath = (String) invocation.arguments().get("file_path");
        String operation = (String) invocation.arguments().get("operation");
        String content = (String) invocation.arguments().get("content");

        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String workspaceId = workspacePath != null ? workspacePath : "default";

        Path workspaceDir = workspacePath != null ? Paths.get(workspacePath)
                : Paths.get(".").toAbsolutePath().normalize();
        Path path;

        if (filePath != null && filePath.startsWith("package:")) {
            DartPackageUriResolver resolver = new DartPackageUriResolver(workspaceDir);
            java.util.Optional<DartPackageUriResolver.ResolvedDartPackageUri> resolved = resolver.resolve(filePath);
            if (resolved.isPresent()) {
                path = resolved.get().file();
            } else {
                return Uni.createFrom().item((ToolResult) AljabrToolResult.error(resolver.describeResolutionFailure(filePath))).subscribeAsCompletionStage();
            }
        } else {
            path = Paths.get(filePath);
        }

        if ("read".equalsIgnoreCase(operation)) {
            return codeTool.open(workspaceId, path)
                    .flatMap(handle -> codeTool.read(workspaceId, path))
                    .map(fileContent -> (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "content", fileContent)))
                    .onFailure().recoverWithItem(e -> {
                        LOG.error("Failed to read file via CodeTool", e);
                        return (ToolResult) AljabrToolResult.error(e.getMessage());
                    }).subscribeAsCompletionStage();
        } else if ("write".equalsIgnoreCase(operation)) {
            return codeTool.open(workspaceId, path)
                    .flatMap(handle -> codeTool.edit(workspaceId, path, content != null ? content : ""))
                    .flatMap(v -> codeTool.save(workspaceId, path))
                    .map(v -> (ToolResult) AljabrToolResult.success("File written successfully via CodeTool"))
                    .onFailure().recoverWithItem(e -> {
                        LOG.error("Failed to write file via CodeTool", e);
                        return (ToolResult) AljabrToolResult.error(e.getMessage());
                    }).subscribeAsCompletionStage();
        } else if ("append".equalsIgnoreCase(operation)) {
            return codeTool.open(workspaceId, path)
                    .flatMap(handle -> codeTool.read(workspaceId, path))
                    .flatMap(existingContent -> {
                        String newContent = existingContent + (content != null ? content : "");
                        return codeTool.edit(workspaceId, path, newContent);
                    })
                    .flatMap(v -> codeTool.save(workspaceId, path))
                    .map(v -> (ToolResult) AljabrToolResult.success("Content appended successfully via CodeTool"))
                    .onFailure().recoverWithItem(e -> {
                        LOG.error("Failed to append file via CodeTool", e);
                        return (ToolResult) AljabrToolResult.error(e.getMessage());
                    }).subscribeAsCompletionStage();
        } else {
            return Uni.createFrom()
                    .item((ToolResult) AljabrToolResult.error("Unknown operation: " + operation)).subscribeAsCompletionStage();
        }
    }

    
}
