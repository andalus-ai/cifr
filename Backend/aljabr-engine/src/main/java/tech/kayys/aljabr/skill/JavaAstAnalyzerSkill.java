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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class JavaAstAnalyzerSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("file_path", "Absolute path to the Java file");
    }


    private static final Logger LOG = Logger.getLogger(JavaAstAnalyzerSkill.class);

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String filePath = (String) invocation.arguments().get("file_path");
        String queryType = (String) invocation.arguments().getOrDefault("query_type", "extract_methods");

        return Uni.createFrom().item(() -> performAstQuery(filePath, queryType))
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool()).subscribeAsCompletionStage();
    }

    private tech.kayys.wayang.tool.ToolResult performAstQuery(String filePath, String queryType) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return AljabrToolResult.error("File does not exist: " + filePath);
            }

            CompilationUnit cu = StaticJavaParser.parse(file);

            if ("extract_methods".equals(queryType)) {
                return extractMethods(cu);
            } else if ("extract_class_body".equals(queryType)) {
                return extractClassBody(cu);
            } else {
                return AljabrToolResult.error("Unsupported query_type: " + queryType);
            }
        } catch (Exception e) {
            LOG.error("AST Parsing failed", e);
            return (ToolResult) AljabrToolResult.error(e.getMessage());
        }
    }

    private tech.kayys.wayang.tool.ToolResult extractMethods(CompilationUnit cu) {
        List<Map<String, Object>> methodsList = new ArrayList<>();

        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration cid : classes) {
            for (MethodDeclaration md : cid.getMethods()) {
                Map<String, Object> methodInfo = new LinkedHashMap<>();
                methodInfo.put("class", cid.getNameAsString());
                methodInfo.put("name", md.getNameAsString());
                methodInfo.put("type", md.getTypeAsString());
                methodInfo.put("parameters", md.getParameters().toString());

                Optional<com.github.javaparser.Position> begin = md.getBegin();
                begin.ifPresent(position -> methodInfo.put("line", position.line));

                methodsList.add(methodInfo);
            }
        }

        return AljabrToolResult.successMap(Map.of("status", "success", "results", methodsList));
    }

    private tech.kayys.wayang.tool.ToolResult extractClassBody(CompilationUnit cu) {
        List<String> classesList = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration cid : classes) {
            // Strip out methods body to just show structure
            ClassOrInterfaceDeclaration skeleton = cid.clone();
            for (MethodDeclaration md : skeleton.getMethods()) {
                md.removeBody();
            }
            classesList.add(skeleton.toString());
        }

        return AljabrToolResult.successMap(Map.of("status", "success", "results", classesList));
    }

    
}
