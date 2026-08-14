package tech.kayys.aljabr.skill;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@ApplicationScoped
public class SemanticSearchSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("Semantic Search (AST)", "Root directory to search");
    }


    private static final Logger LOG = Logger.getLogger(SemanticSearchSkill.class);

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String searchPath = (String) invocation.arguments().get("search_path");
        String targetType = (String) invocation.arguments().get("target_type");
        String targetName = (String) invocation.arguments().get("target_name");

        return Uni.createFrom().item(() -> performSearch(searchPath, targetType, targetName))
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool()).subscribeAsCompletionStage();
    }

    private tech.kayys.wayang.tool.ToolResult performSearch(String rootPath, String targetType, String targetName) {
        Path root = Paths.get(rootPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return AljabrToolResult.error("Invalid search path");
        }

        List<Map<String, Object>> matches = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            CompilationUnit cu = StaticJavaParser.parse(path);

                            if ("class".equalsIgnoreCase(targetType)) {
                                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                                    if (c.getNameAsString().equals(targetName)) {
                                        Map<String, Object> match = new LinkedHashMap<>();
                                        match.put("file", path.toAbsolutePath().toString());
                                        match.put("type", "class");
                                        match.put("name", targetName);
                                        c.getBegin().ifPresent(pos -> match.put("line", pos.line));
                                        matches.add(match);
                                    }
                                });
                            } else if ("method".equalsIgnoreCase(targetType)) {
                                cu.findAll(MethodDeclaration.class).forEach(m -> {
                                    if (m.getNameAsString().equals(targetName)) {
                                        Map<String, Object> match = new LinkedHashMap<>();
                                        match.put("file", path.toAbsolutePath().toString());
                                        match.put("type", "method");
                                        match.put("name", targetName);
                                        m.getBegin().ifPresent(pos -> match.put("line", pos.line));
                                        matches.add(match);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            // Ignore parse errors on individual files
                        }
                    });

            return AljabrToolResult.successMap(Map.of("status", "success", "matches", matches));
        } catch (Exception e) {
            LOG.error("Failed to search AST", e);
            return AljabrToolResult.error(e.getMessage());
        }
    }

    
}
