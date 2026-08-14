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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@ApplicationScoped
public class WorkspaceNavigationSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("directory_path", "Absolute or relative path to the directory to list");
    }


    private static final Logger LOG = Logger.getLogger(WorkspaceNavigationSkill.class);

    

    

    

    

    

        

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String directoryPath = (String) invocation.arguments().get("directory_path");

        return Uni.createFrom().item(() -> performListDirectory(directoryPath))
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool()).subscribeAsCompletionStage();
    }

    private tech.kayys.wayang.tool.ToolResult performListDirectory(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            File file = path.toFile();

            if (!file.exists()) {
                return AljabrToolResult.error("Directory does not exist: " + directoryPath);
            }
            if (!file.isDirectory()) {
                return AljabrToolResult.error("Path is not a directory: " + directoryPath);
            }

            List<Map<String, Object>> fileList = new ArrayList<>();
            try (Stream<Path> stream = Files.list(path)) {
                stream.forEach(p -> {
                    Map<String, Object> fileInfo = new LinkedHashMap<>();
                    File f = p.toFile();
                    fileInfo.put("name", f.getName());
                    fileInfo.put("is_dir", f.isDirectory());
                    fileInfo.put("size", f.length());
                    fileList.add(fileInfo);
                });
            }

            return AljabrToolResult.successMap(Map.of("status", "success", "files", fileList));

        } catch (Exception e) {
            LOG.error("Failed to list directory", e);
            return (ToolResult) AljabrToolResult.error(e.getMessage());
        }
    }

    
}
