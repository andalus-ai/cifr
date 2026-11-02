package tech.kayys.cifr.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves Dart {@code package:} URIs by reading package_config files produced
 * by {@code dart pub get} or {@code flutter pub get}.
 */
public final class DartPackageUriResolver implements WorkspaceUriResolver {

    private static final int MAX_CONFIG_DEPTH = 6;
    private static final int MAX_CONFIG_FILES = 50;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path workspace;

    public DartPackageUriResolver(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    @Override
    public boolean canResolve(String rawUri) {
        return rawUri != null && rawUri.startsWith("package:");
    }

    @Override
    public Optional<Path> resolve(String rawUri) {
        return resolveDartUri(rawUri).map(ResolvedDartPackageUri::file);
    }

    @Override
    public String describeFailure(String rawUri) {
        return describeResolutionFailure(rawUri);
    }

    public Optional<ResolvedDartPackageUri> resolveDartUri(String rawPath) {
        Optional<DartPackageUri> packageUri = DartPackageUri.parse(rawPath);
        if (packageUri.isEmpty()) {
            return Optional.empty();
        }

        for (Path configFile : packageConfigFiles()) {
            Optional<ResolvedDartPackageUri> resolved = resolveFromConfig(configFile, packageUri.get());
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    public String describeResolutionFailure(String rawPath) {
        Optional<DartPackageUri> packageUri = DartPackageUri.parse(rawPath);
        if (packageUri.isEmpty()) {
            return "Not a Dart package URI: " + rawPath;
        }

        List<Path> configs = packageConfigFiles();
        if (configs.isEmpty()) {
            return "Dart package URI could not be resolved: " + rawPath
                    + ". No .dart_tool/package_config.json was found under " + workspace
                    + ". Run dart pub get or flutter pub get, then retry read_file with the package: URI.";
        }

        String configList = configs.stream()
                .map(path -> workspace.relativize(path).toString())
                .sorted()
                .limit(5)
                .reduce((left, right) -> left + ", " + right)
                .orElse("package_config.json");
        return "Dart package URI could not be resolved: " + rawPath
                + ". Package '" + packageUri.get().packageName() + "' was not found in " + configList + ".";
    }

    private Optional<ResolvedDartPackageUri> resolveFromConfig(Path configFile, DartPackageUri packageUri) {
        try {
            JsonNode packages = JSON.readTree(configFile.toFile()).path("packages");
            if (!packages.isArray()) {
                return Optional.empty();
            }

            for (JsonNode entry : packages) {
                if (!packageUri.packageName().equals(entry.path("name").asText())) {
                    continue;
                }
                Optional<Path> packageRoot = resolveRootUri(configFile, entry.path("rootUri").asText());
                if (packageRoot.isEmpty()) {
                    return Optional.empty();
                }

                String packageUriPath = uriPathOrRaw(entry.path("packageUri").asText("lib/"));
                Path sourceRoot = packageRoot.get().resolve(packageUriPath).normalize();
                Path target = sourceRoot.resolve(packageUri.pathInsidePackage()).normalize();
                if (!target.startsWith(sourceRoot)) {
                    return Optional.empty();
                }

                return Optional.of(new ResolvedDartPackageUri(
                        packageUri.raw(),
                        packageUri.packageName(),
                        target,
                        packageRoot.get(),
                        configFile));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> resolveRootUri(Path configFile, String rootUri) {
        if (rootUri == null || rootUri.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(rootUri);
            if (uri.isAbsolute() && "file".equalsIgnoreCase(uri.getScheme())) {
                return Optional.of(Path.of(uri).normalize());
            }
            return Optional.of(configFile.getParent().resolve(rootUri).normalize());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String uriPathOrRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return "lib/";
        }
        try {
            URI uri = URI.create(raw);
            if (uri.getPath() != null && !uri.getPath().isBlank()) {
                return uri.getPath();
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    private List<Path> packageConfigFiles() {
        List<Path> matches = new ArrayList<>();
        if (!Files.exists(workspace)) {
            return matches;
        }

        try {
            Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Thread.currentThread().isInterrupted()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (workspace.relativize(dir).getNameCount() > MAX_CONFIG_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (!dir.equals(workspace) && !name.equals(".dart_tool") && name.startsWith(".")
                            && !name.equals(".dart_tool")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.endsWith(Path.of(".dart_tool", "package_config.json"))) {
                        matches.add(file.toAbsolutePath().normalize());
                        if (matches.size() >= MAX_CONFIG_FILES) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }

        return matches;
    }

    public record DartPackageUri(String raw, String packageName, String pathInsidePackage) {
        public static Optional<DartPackageUri> parse(String raw) {
            if (raw == null || !raw.startsWith("package:")) {
                return Optional.empty();
            }
            String withoutScheme = raw.substring("package:".length());
            int slash = withoutScheme.indexOf('/');
            if (slash <= 0 || slash == withoutScheme.length() - 1) {
                return Optional.empty();
            }
            String pkg = withoutScheme.substring(0, slash);
            String rest = withoutScheme.substring(slash + 1);
            return Optional.of(new DartPackageUri(raw, pkg, rest));
        }
    }

    public record ResolvedDartPackageUri(
            String raw,
            String packageName,
            Path file,
            Path packageRoot,
            Path packageConfig) {
    }
}
