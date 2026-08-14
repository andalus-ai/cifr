package tech.kayys.aljabr.skill;

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
public final class DartPackageUriResolver {

    private static final int MAX_CONFIG_DEPTH = 6;
    private static final int MAX_CONFIG_FILES = 50;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path workspace;

    public DartPackageUriResolver(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    public Optional<ResolvedDartPackageUri> resolve(String rawPath) {
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
        } catch (IOException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Path> resolveRootUri(Path configFile, String rootUri) {
        if (rootUri == null || rootUri.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(rootUri);
            if (uri.isAbsolute()) {
                if (!"file".equalsIgnoreCase(uri.getScheme())) {
                    return Optional.empty();
                }
                return Optional.of(Path.of(uri).toAbsolutePath().normalize());
            }
            Path configDir = configFile.getParent();
            if (configDir == null) {
                return Optional.empty();
            }
            return Optional.of(configDir.resolve(uri.getPath()).normalize());
        } catch (IllegalArgumentException ignored) {
            // Fall back to treating the value as a plain relative path below.
        }

        Path configDir = configFile.getParent();
        if (configDir == null) {
            return Optional.empty();
        }
        return Optional.of(configDir.resolve(rootUri).normalize());
    }

    private String uriPathOrRaw(String value) {
        try {
            return URI.create(value).getPath();
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private List<Path> packageConfigFiles() {
        List<Path> configs = new ArrayList<>();
        Path rootConfig = workspace.resolve(".dart_tool/package_config.json").normalize();
        if (Files.isRegularFile(rootConfig)) {
            configs.add(rootConfig);
        }

        try {
            Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (configs.size() >= MAX_CONFIG_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (workspace.relativize(dir).getNameCount() > MAX_CONFIG_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (name.equals(".git") || name.equals("build") || name.equals("target")
                            || name.equals("node_modules")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (configs.size() >= MAX_CONFIG_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (file.getFileName() == null
                            || !"package_config.json".equals(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path parent = file.getParent();
                    if (parent != null && ".dart_tool".equals(parent.getFileName().toString())
                            && !configs.contains(file)) {
                        configs.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
    } catch (IOException ignored) {
            // A missing or partially unreadable workspace should not break ordinary
            // read_file calls.
        }
        return configs;
    }

    /**
     * A parsed Dart {@code package:} URI split into the package name and source
     * path relative to the package's public source root.
     */
    private record DartPackageUri(String raw, String packageName, String pathInsidePackage) {

        static Optional<DartPackageUri> parse(String rawPath) {
            if (rawPath == null || !rawPath.startsWith("package:")) {
                return Optional.empty();
            }
            String rest = rawPath.substring("package:".length());
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash == rest.length() - 1) {
                return Optional.empty();
            }
            String packageName = rest.substring(0, slash);
            String pathInsidePackage = rest.substring(slash + 1);
            return Optional.of(new DartPackageUri(rawPath, packageName, pathInsidePackage));
        }
    }

    /**
     * Resolved filesystem location for a Dart {@code package:} URI.
     */
    public record ResolvedDartPackageUri(
            String raw,
            String packageName,
            Path file,
            Path packageRoot,
            Path packageConfig) {
    }
}
