package tech.kayys.cifr.engine.index.indexer;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.index.FileIndex;
import tech.kayys.cifr.engine.index.FileRecord;
import tech.kayys.cifr.engine.index.FileRole;
import tech.kayys.cifr.engine.index.WorkspaceFingerprint;
import tech.kayys.cifr.engine.index.dependency.Dependency;
import tech.kayys.cifr.engine.index.dependency.DependencyIndex;
import tech.kayys.cifr.engine.index.dependency.DependencyKind;
import tech.kayys.cifr.engine.index.relation.RelationIndex;
import tech.kayys.cifr.engine.index.ide.CifrIdeWorkspaceRegistry;
import tech.kayys.cifr.engine.index.symbol.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ApplicationScoped
public class DefaultWorkspaceIndexer implements WorkspaceIndexer {

    private static final Logger LOG = Logger.getLogger(DefaultWorkspaceIndexer.class);

    private static final Set<String> IGNORED_DIRS = Set.of(
        ".git", "target", "build", ".dart_tool", "node_modules", ".gradle",
        ".idea", ".vscode", ".gemini", "dist", "bin", "obj", ".system_generated"
    );

    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile(
        "\\b(?:public|protected|private)?\\s*(?:static\\s+|abstract\\s+|final\\s+)*(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");
    private static final Pattern JAVA_METHOD_PATTERN = Pattern.compile(
        "\\b(?:public|protected|private)?\\s+(?:static\\s+|final\\s+)*[A-Za-z0-9_<>,\\s\\[\\]]+\\s+([A-Za-z0-9_]+)\\s*\\(");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^\\s*import\\s+([A-Za-z0-9_\\.]+);?", Pattern.MULTILINE);

    @Inject
    FileIndex fileIndex;

    @Inject
    SymbolIndex symbolIndex;

    @Inject
    DependencyIndex dependencyIndex;

    @Inject
    RelationIndex relationIndex;

    @Inject
    CifrIdeWorkspaceRegistry ideWorkspaces;

    public DefaultWorkspaceIndexer() {
    }

    public DefaultWorkspaceIndexer(FileIndex fileIndex, SymbolIndex symbolIndex, DependencyIndex dependencyIndex, RelationIndex relationIndex) {
        this.fileIndex = fileIndex;
        this.symbolIndex = symbolIndex;
        this.dependencyIndex = dependencyIndex;
        this.relationIndex = relationIndex;
    }

    @Override
    public Uni<IndexResult> index(Path workspaceRoot, String workspaceId) {
        return Uni.createFrom().item(() -> {
            long startTime = System.currentTimeMillis();
            Path root = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
            String effectiveWorkspaceId = workspaceId == null || workspaceId.isBlank() ? "default-workspace" : workspaceId;
            ensureIdeSession(root, effectiveWorkspaceId);
            if (symbolIndex != null) symbolIndex.invalidateResolverCache(effectiveWorkspaceId);

            AtomicInteger filesCount = new AtomicInteger(0);
            AtomicInteger symbolsCount = new AtomicInteger(0);
            AtomicInteger depsCount = new AtomicInteger(0);

            if (!Files.exists(root)) {
                if (fileIndex != null) {
                    for (Path removed : fileIndex.removeMissingFiles(effectiveWorkspaceId, Set.of())) {
                        if (symbolIndex != null) symbolIndex.removeFile(removed, effectiveWorkspaceId);
                        if (dependencyIndex != null) dependencyIndex.removeFile(removed.toString(), effectiveWorkspaceId);
                    }
                }
                if (symbolIndex != null) {
                    symbolIndex.invalidateResolverCache(effectiveWorkspaceId);
                }
                return new IndexResult(0, 0, 0, new WorkspaceFingerprint("empty", "1.0", "empty"), 0);
            }

            Set<Path> indexedPaths = new HashSet<>();
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(root.relativize(path)))
                    .forEach(filePath -> {
                        Path relative = root.relativize(filePath).normalize();
                        indexedPaths.add(relative);
                        indexSingleFile(root, relative, effectiveWorkspaceId, filesCount, symbolsCount, depsCount);
                    });
                if (fileIndex != null) {
                    for (Path removed : fileIndex.removeMissingFiles(effectiveWorkspaceId, indexedPaths)) {
                        if (symbolIndex != null) symbolIndex.removeFile(removed, effectiveWorkspaceId);
                        if (dependencyIndex != null) dependencyIndex.removeFile(removed.toString(), effectiveWorkspaceId);
                    }
                    if (symbolIndex != null) symbolIndex.invalidateResolverCache(effectiveWorkspaceId);
                }
            } catch (IOException e) {
                LOG.errorf("Failed to index workspace at %s: %s", root, e.getMessage());
            }

            long duration = System.currentTimeMillis() - startTime;
            String repoHash = "hash-" + filesCount.get() + "-" + symbolsCount.get() + "-" + depsCount.get();
            WorkspaceFingerprint fingerprint = new WorkspaceFingerprint(repoHash, "1.0", "cfg-" + effectiveWorkspaceId);

            LOG.infof("Indexed workspace: %d files, %d symbols, %d dependencies in %d ms",
                filesCount.get(), symbolsCount.get(), depsCount.get(), duration);

            return new IndexResult(filesCount.get(), symbolsCount.get(), depsCount.get(), fingerprint, duration);
        });
    }

    @Override
    public Uni<Void> updateFile(Path file, Path workspaceRoot, String workspaceId) {
        return Uni.createFrom().item(() -> {
            Path root = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
            String effectiveWorkspaceId = workspaceId == null || workspaceId.isBlank() ? "default-workspace" : workspaceId;
            ensureIdeSession(root, effectiveWorkspaceId);
            Path relative = root.relativize(root.resolve(file).normalize());
            removeIndexedFile(relative, effectiveWorkspaceId);
            if (symbolIndex != null) symbolIndex.invalidateResolverCache(effectiveWorkspaceId);

            AtomicInteger filesCount = new AtomicInteger(0);
            AtomicInteger symbolsCount = new AtomicInteger(0);
            AtomicInteger depsCount = new AtomicInteger(0);

            indexSingleFile(root, relative, effectiveWorkspaceId, filesCount, symbolsCount, depsCount);
            return null;
        });
    }

    @Override
    public Uni<Void> removeFile(Path file, String workspaceId) {
        return Uni.createFrom().item(() -> {
            if (file == null || workspaceId == null || workspaceId.isBlank()) return null;
            removeIndexedFile(file.normalize(), workspaceId);
            if (symbolIndex != null) symbolIndex.invalidateResolverCache(workspaceId);
            return null;
        });
    }

    @Override
    public Uni<Void> closeWorkspace(String workspaceId) {
        return Uni.createFrom().item(() -> {
            if (workspaceId != null && !workspaceId.isBlank() && ideWorkspaces != null) {
                ideWorkspaces.closeWorkspace(workspaceId);
            }
            return null;
        });
    }

    private void removeIndexedFile(Path relative, String workspaceId) {
        if (fileIndex != null) fileIndex.removeFile(relative, workspaceId);
        if (symbolIndex != null) symbolIndex.removeFile(relative, workspaceId);
        if (dependencyIndex != null) dependencyIndex.removeFile(relative.toString(), workspaceId);
    }

    private void ensureIdeSession(Path root, String workspaceId) {
        if (ideWorkspaces == null || ideWorkspaces.isOpen(workspaceId)) return;
        try {
            ideWorkspaces.openWorkspace(workspaceId, root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warnf("Interrupted while starting CIFR IDE for workspace %s", workspaceId);
        } catch (Exception e) {
            // Keep the lightweight indexer available when the optional IDE binary is absent.
            LOG.warnf("CIFR IDE unavailable for workspace %s: %s", workspaceId, e.getMessage());
        }
    }

    private boolean isIgnored(Path relativePath) {
        for (Path part : relativePath) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private void indexSingleFile(Path root, Path relative, String workspaceId, AtomicInteger filesCount, AtomicInteger symbolsCount, AtomicInteger depsCount) {
        Path absPath = root.resolve(relative);
        String fileName = relative.getFileName().toString();
        String lang = detectLanguage(fileName);
        boolean isTest = fileName.endsWith("Test.java") || fileName.endsWith("_test.dart") || fileName.contains(".test.");
        FileRole role = isTest ? FileRole.TEST : FileRole.SOURCE;

        try {
            long size = Files.size(absPath);
            String content = Files.readString(absPath);
            String contentHash = String.valueOf(content.hashCode());

            removeIndexedFile(relative, workspaceId);
            FileRecord record = new FileRecord(relative, contentHash, size, lang, false, isTest, role, workspaceId);
            if (fileIndex != null) {
                fileIndex.registerFile(record);
            }
            filesCount.incrementAndGet();

            // Extract symbols if source code
            if ("java".equals(lang) || "dart".equals(lang) || "typescript".equals(lang) || "python".equals(lang)) {
                extractSymbols(relative, content, symbolsCount, workspaceId);
                extractDependencies(relative, content, depsCount, workspaceId);
            }

        } catch (IOException e) {
            LOG.warnf("Could not read file for indexing: %s", relative);
        }
    }

    private void extractSymbols(Path relative, String content, AtomicInteger symbolsCount, String workspaceId) {
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
                continue;
            }

            Matcher typeMatcher = JAVA_TYPE_PATTERN.matcher(line);
            if (typeMatcher.find()) {
                String typeName = typeMatcher.group(1);
                SymbolId id = new SymbolId(typeName, "type", relative.toString());
                SourceRange range = new SourceRange(i + 1, i + 1, 0, line.length());
                SymbolInfo info = new SymbolInfo(id, typeName, SymbolKind.CLASS, relative, range, Optional.empty(), workspaceId);

                if (symbolIndex != null) {
                    symbolIndex.registerSymbol(info);
                }
                symbolsCount.incrementAndGet();
            }

            Matcher methodMatcher = JAVA_METHOD_PATTERN.matcher(line);
            if (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                if (!methodName.equals("if") && !methodName.equals("for") && !methodName.equals("while") && !methodName.equals("switch")) {
                    SymbolId id = new SymbolId(methodName, "method", relative.toString());
                    SourceRange range = new SourceRange(i + 1, i + 1, 0, line.length());
                    SymbolInfo info = new SymbolInfo(id, methodName, SymbolKind.METHOD, relative, range, Optional.empty(), workspaceId);

                    if (symbolIndex != null) {
                        symbolIndex.registerSymbol(info);
                    }
                    symbolsCount.incrementAndGet();
                }
            }
        }
    }

    private void extractDependencies(Path relative, String content, AtomicInteger depsCount, String workspaceId) {
        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            String imported = importMatcher.group(1);
            Dependency dep = new Dependency(relative.toString(), imported, DependencyKind.PACKAGE, workspaceId);
            if (dependencyIndex != null) {
                dependencyIndex.registerDependency(dep);
            }
            depsCount.incrementAndGet();
        }
    }

    private String detectLanguage(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".dart")) return "dart";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        return "text";
    }
}
