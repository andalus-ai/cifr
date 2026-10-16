package tech.kayys.aljabr.engine.knowledge.graph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.index.FileIndex;
import tech.kayys.aljabr.engine.index.FileRecord;
import tech.kayys.aljabr.engine.index.symbol.SymbolIndex;
import tech.kayys.aljabr.engine.repo.GitRepositoryService;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class RepositoryGraphBuilder {
    private static final Logger LOG = Logger.getLogger(RepositoryGraphBuilder.class);

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern CLASS_DECL_PATTERN = Pattern.compile("(?:public|protected|private|abstract|final|static|sealed)*\\s*(class|interface|enum|record)\\s+([a-zA-Z0-9_]+)(?:<[^>]+>)?(?:\\s+extends\\s+([a-zA-Z0-9_., <>]+))?(?:\\s+implements\\s+([a-zA-Z0-9_., <>]+))?");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?:public|protected|private|static|final|synchronized|abstract|default)\\s+(?:<[^>]+>\\s+)?([a-zA-Z0-9_<>\\[\\]]+)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");

    @Inject FileIndex fileIndex;
    @Inject SymbolIndex symbolIndex;
    @Inject GitRepositoryService gitService;

    private volatile RepositoryGraph currentGraph;
    private volatile String workspaceFingerprint;
    private final Map<String, Long> lastIndexed = new ConcurrentHashMap<>();

    public Uni<RepositoryGraph> build(Path workspaceRoot) {
        Path root = workspaceRoot != null ? workspaceRoot : Path.of(".");
        String fingerprint = workspaceFingerprint(root);

        if (currentGraph != null && fingerprint.equals(workspaceFingerprint) && !needsRebuild(root)) {
            return Uni.createFrom().item(currentGraph);
        }

        LOG.infof("Building repository graph for %s", root);
        RepositoryGraph graph = new RepositoryGraph();

        return fileIndex.scanWorkspace(root)
            .flatMap(records -> {
                for (FileRecord record : records) {
                    processFile(record, graph, root);
                }

                addGitHistory(graph, root);
                buildModuleStructure(graph);

                currentGraph = graph;
                workspaceFingerprint = fingerprint;
                lastIndexed.put(root.toString(), System.currentTimeMillis());

                LOG.infof("Built repository graph: %d nodes, %d outgoing edges",
                        graph.size(), graph.outgoingEdges().size());

                return Uni.createFrom().item(graph);
            })
            .onFailure().recoverWithItem(err -> {
                LOG.warnf("Failed to complete full scan for graph: %s", err.getMessage());
                if (currentGraph == null) {
                    currentGraph = graph;
                }
                return currentGraph;
            });
    }

    private void processFile(FileRecord record, RepositoryGraph graph, Path workspaceRoot) {
        Path filePath = record.path() != null ? record.path() : Path.of("unknown");
        Path absolutePath = workspaceRoot.resolve(filePath).normalize();
        String pathStr = filePath.toString().replace('\\', '/');
        String fileName = filePath.getFileName().toString();

        if (fileName.endsWith("Test.java") || fileName.endsWith("IT.java") || fileName.endsWith("Spec.java")) {
            RepositoryNode.Test testNode = new RepositoryNode.Test("test:" + pathStr, fileName, pathStr);
            graph.addNode(testNode);
        }

        if (!fileName.endsWith(".java") || !Files.exists(absolutePath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(absolutePath);
            String packageName = "default";
            List<String> imports = new ArrayList<>();
            Set<RepositoryNode.Method> methods = new HashSet<>();
            String primaryClassName = null;
            ClassKind classKind = ClassKind.CLASS;
            String superClassName = null;
            List<String> interfaces = new ArrayList<>();

            for (String line : lines) {
                Matcher pkgMatcher = PACKAGE_PATTERN.matcher(line);
                if (pkgMatcher.find()) {
                    packageName = pkgMatcher.group(1);
                    continue;
                }

                Matcher importMatcher = IMPORT_PATTERN.matcher(line);
                if (importMatcher.find()) {
                    imports.add(importMatcher.group(1));
                    continue;
                }

                Matcher classMatcher = CLASS_DECL_PATTERN.matcher(line);
                if (classMatcher.find() && primaryClassName == null) {
                    String kindStr = classMatcher.group(1);
                    primaryClassName = classMatcher.group(2);
                    classKind = switch (kindStr) {
                        case "interface" -> ClassKind.INTERFACE;
                        case "enum" -> ClassKind.ENUM;
                        case "record" -> ClassKind.RECORD;
                        default -> ClassKind.CLASS;
                    };
                    superClassName = classMatcher.group(3);
                    String impls = classMatcher.group(4);
                    if (impls != null) {
                        for (String iface : impls.split(",")) {
                            interfaces.add(iface.trim().replaceAll("<.*>", ""));
                        }
                    }
                    continue;
                }

                Matcher methodMatcher = METHOD_PATTERN.matcher(line);
                if (methodMatcher.find()) {
                    String methodName = methodMatcher.group(2);
                    String returnType = methodMatcher.group(1);
                    String params = methodMatcher.group(3);
                    if (!methodName.equals("if") && !methodName.equals("for") && !methodName.equals("while") && !methodName.equals("switch")) {
                        String methodId = (primaryClassName != null ? primaryClassName : "Anon") + "#" + methodName;
                        methods.add(new RepositoryNode.Method(methodId, methodName, returnType + " " + methodName + "(" + params + ")", Set.of()));
                    }
                }
            }

            if (primaryClassName != null) {
                String qualifiedName = packageName.equals("default") ? primaryClassName : packageName + "." + primaryClassName;
                RepositoryNode.Class classNode = new RepositoryNode.Class(
                        qualifiedName,
                        primaryClassName,
                        classKind,
                        methods,
                        Set.of(),
                        Set.of()
                );
                graph.addNode(classNode);

                // Add import edges
                for (String imported : imports) {
                    graph.addEdge(qualifiedName, imported);
                }

                // Add inheritance edges
                if (superClassName != null && !superClassName.isBlank()) {
                    graph.addEdge(qualifiedName, superClassName.trim());
                }
                for (String iface : interfaces) {
                    graph.addEdge(qualifiedName, iface);
                }

                // Connect test to tested class
                if (fileName.endsWith("Test.java") || fileName.endsWith("IT.java")) {
                    String targetClassName = qualifiedName.replace("Test", "").replace("IT", "");
                    graph.addEdge("test:" + pathStr, targetClassName);
                }
            }
        } catch (IOException e) {
            LOG.debugf("Could not parse file %s: %s", filePath, e.getMessage());
        }
    }

    private void addGitHistory(RepositoryGraph graph, Path workspaceRoot) {
        try {
            if (gitService != null) {
                RepositorySnapshot snapshot = gitService.captureSnapshot(workspaceRoot);
                if (snapshot != null && snapshot.commitHash() != null) {
                    String commitId = "commit:" + snapshot.commitHash();
                    RepositoryNode.GitCommit commit = new RepositoryNode.GitCommit(
                            commitId,
                            snapshot.commitHash().substring(0, Math.min(8, snapshot.commitHash().length())),
                            snapshot.commitHash(),
                            "Commit " + snapshot.commitHash(),
                            Instant.now(),
                            snapshot.changedFiles() != null ? new HashSet<>(snapshot.changedFiles()) : Set.of()
                    );
                    graph.addNode(commit);

                    if (snapshot.changedFiles() != null) {
                        for (String changedFile : snapshot.changedFiles()) {
                            graph.addEdge(commitId, "file:" + changedFile);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not capture Git history for graph: %s", e.getMessage());
        }
    }

    private void buildModuleStructure(RepositoryGraph graph) {
        Map<String, Set<String>> packages = new ConcurrentHashMap<>();

        for (var entry : graph.nodes().entrySet()) {
            if (entry.getValue() instanceof RepositoryNode.Class clazz) {
                String packageName = extractPackage(clazz.id());
                packages.computeIfAbsent(packageName, k -> ConcurrentHashMap.newKeySet()).add(clazz.id());
            }
        }

        for (var entry : packages.entrySet()) {
            String packageName = entry.getKey();
            String packageId = "pkg:" + packageName;
            Set<RepositoryNode.Class> classes = entry.getValue().stream()
                    .map(id -> graph.getNode(id).orElse(null))
                    .filter(n -> n instanceof RepositoryNode.Class)
                    .map(n -> (RepositoryNode.Class) n)
                    .collect(Collectors.toSet());

            RepositoryNode.Package pkgNode = new RepositoryNode.Package(packageId, packageName, classes);
            graph.addNode(pkgNode);

            for (String classId : entry.getValue()) {
                graph.addEdge(classId, packageId);
            }
        }
    }

    private String extractPackage(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "default";
    }

    private String workspaceFingerprint(Path workspaceRoot) {
        try {
            return String.valueOf(workspaceRoot.toAbsolutePath().hashCode());
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean needsRebuild(Path workspaceRoot) {
        Long last = lastIndexed.get(workspaceRoot.toString());
        return last == null || (System.currentTimeMillis() - last > 60000);
    }

    public RepositoryGraph snapshot() {
        return currentGraph != null ? currentGraph : new RepositoryGraph();
    }
}
