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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@ApplicationScoped
public class FilePatchSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("target_file", "Absolute path to the target file");
    }


    private static final Logger LOG = Logger.getLogger(FilePatchSkill.class);

    

    

    

    

    

    @jakarta.inject.Inject
    tech.kayys.muqabalah.api.CodeTool codeTool;

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String targetFile = (String) invocation.arguments().get("target_file");
        String targetContent = (String) invocation.arguments().get("target_content");
        String replacementContent = (String) invocation.arguments().get("replacement_content");

        // Extract workspacePath if provided, else use default. (Agents should provide
        // workspace_path going forward)
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String workspaceId = workspacePath != null ? workspacePath : "default";

        if (targetFile == null || targetContent == null || replacementContent == null) {
            return Uni.createFrom()
                    .item((ToolResult) AljabrToolResult.error("Missing required parameters")).subscribeAsCompletionStage();
        }

        Path path = Paths.get(targetFile);

        // First, ensure the document is opened and read its current state from the IDE
        // buffer
        return codeTool.open(workspaceId, path)
                .flatMap(handle -> codeTool.read(workspaceId, path))
                .flatMap(content -> {
                    String finalTarget = targetContent;
                    String finalContent = content;
                    String finalReplacement = replacementContent;

                    int index = finalContent.indexOf(finalTarget);
                    if (index == -1) {
                        // Normalize line endings
                        String normalizedContent = finalContent.replace("\r\n", "\n");
                        String normalizedTarget = finalTarget.replace("\r\n", "\n");
                        if (normalizedContent.indexOf(normalizedTarget) == -1) {
                            return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Target content not found in the file. Ensure exact match including whitespace."));
                        } else {
                            finalContent = normalizedContent;
                            finalTarget = normalizedTarget;
                            finalReplacement = finalReplacement.replace("\r\n", "\n");
                            index = finalContent.indexOf(finalTarget);
                        }
                    }

                    int lastIndex = finalContent.lastIndexOf(finalTarget);
                    if (index != lastIndex) {
                        return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Target content is not unique. Found multiple matches. Be more specific."));
                    }

                    String updatedContent = finalContent.replace(finalTarget, finalReplacement);

                    // Update the IDE buffer and flush to disk
                    return codeTool.edit(workspaceId, path, updatedContent)
                            .flatMap(v -> codeTool.save(workspaceId, path))
                            .map(v -> (ToolResult) AljabrToolResult.successMap(Map.of(
                                    "status", "success",
                                    "message", "Successfully applied patch to " + targetFile,
                                    "result", "File updated.")));
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.error("Failed to patch file", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    
}
