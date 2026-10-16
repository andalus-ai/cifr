package tech.kayys.aljabr.engine.index;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;

@ApplicationScoped
public class DefaultFileClassifier implements FileClassifier {

    @Override
    public FileRole classifyRole(Path path) {
        String pathStr = path.toString().toLowerCase();
        
        if (pathStr.contains("/node_modules/") || pathStr.contains("/vendor/")) {
            return FileRole.VENDOR;
        }
        if (pathStr.contains("/target/") || pathStr.contains("/build/") || pathStr.contains("/dist/")) {
            return FileRole.BUILD_ARTIFACT;
        }
        if (pathStr.contains("/generated/")) {
            return FileRole.GENERATED;
        }
        if (pathStr.endsWith(".md") || pathStr.contains("/docs/")) {
            return FileRole.DOCUMENTATION;
        }
        if (pathStr.endsWith(".json") || pathStr.endsWith(".yml") || pathStr.endsWith(".yaml") 
            || pathStr.endsWith(".xml") || pathStr.endsWith(".properties")) {
            return FileRole.CONFIG;
        }
        if (isTest(path)) {
            return FileRole.TEST;
        }
        if (pathStr.endsWith(".java") || pathStr.endsWith(".js") || pathStr.endsWith(".ts") 
            || pathStr.endsWith(".py") || pathStr.endsWith(".go") || pathStr.endsWith(".rs") 
            || pathStr.endsWith(".cpp") || pathStr.endsWith(".c") || pathStr.endsWith(".h")) {
            return FileRole.SOURCE;
        }
        return FileRole.UNKNOWN;
    }

    @Override
    public String detectLanguage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1) {
            return "unknown";
        }
        String ext = name.substring(lastDot + 1);
        return switch (ext) {
            case "java" -> "java";
            case "js", "jsx" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "py" -> "python";
            case "go" -> "go";
            case "rs" -> "rust";
            case "cpp", "c", "h", "hpp" -> "c++";
            case "json" -> "json";
            case "yml", "yaml" -> "yaml";
            case "xml" -> "xml";
            case "md" -> "markdown";
            default -> "unknown";
        };
    }

    @Override
    public boolean isGenerated(Path path) {
        String pathStr = path.toString().toLowerCase();
        return pathStr.contains("/generated/") || pathStr.contains("/target/generated-sources/");
    }

    @Override
    public boolean isTest(Path path) {
        String pathStr = path.toString().toLowerCase();
        return pathStr.contains("/test/") 
            || pathStr.contains("test.java") 
            || pathStr.contains("test.js") 
            || pathStr.contains("test.ts")
            || pathStr.contains("spec.js")
            || pathStr.contains("spec.ts");
    }
}
