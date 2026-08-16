package tech.kayys.aljabr.engine.orchestrator.toolchain;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@ApplicationScoped
public class DefaultProjectDetector implements ProjectDetector {

    @Override
    public Uni<ProjectToolchain> detect(Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            Path root = workspaceRoot != null ? workspaceRoot.toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
            Map<String, String> meta = new HashMap<>();

            // 1. Swift & Objective-C / Apple Platform Build Systems
            if (Files.exists(root.resolve("Package.swift"))) {
                meta.put("buildFile", "Package.swift");
                return new ProjectToolchain(BuildSystem.SWIFT_PM, Set.of("Swift"), root, meta);
            }
            if (hasXcodeProject(root)) {
                meta.put("buildFile", "Xcode");
                return new ProjectToolchain(BuildSystem.XCODE, Set.of("Swift", "Objective-C"), root, meta);
            }

            // 2. C/C++ Build Systems
            if (Files.exists(root.resolve("CMakeLists.txt"))) {
                meta.put("buildFile", "CMakeLists.txt");
                return new ProjectToolchain(BuildSystem.CMAKE, Set.of("C", "C++"), root, meta);
            }
            if (Files.exists(root.resolve("meson.build"))) {
                meta.put("buildFile", "meson.build");
                return new ProjectToolchain(BuildSystem.MESON, Set.of("C", "C++"), root, meta);
            }
            if (Files.exists(root.resolve("Makefile")) || Files.exists(root.resolve("makefile"))) {
                meta.put("buildFile", Files.exists(root.resolve("Makefile")) ? "Makefile" : "makefile");
                return new ProjectToolchain(BuildSystem.MAKE, Set.of("C", "C++"), root, meta);
            }

            // 3. JVM & Web Build Systems
            if (Files.exists(root.resolve("pom.xml"))) {
                meta.put("buildFile", "pom.xml");
                return new ProjectToolchain(BuildSystem.MAVEN, Set.of("Java"), root, meta);
            }
            if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
                meta.put("buildFile", Files.exists(root.resolve("build.gradle.kts")) ? "build.gradle.kts" : "build.gradle");
                return new ProjectToolchain(BuildSystem.GRADLE, Set.of("Java", "Kotlin"), root, meta);
            }
            if (Files.exists(root.resolve("pubspec.yaml"))) {
                meta.put("buildFile", "pubspec.yaml");
                return new ProjectToolchain(BuildSystem.FLUTTER, Set.of("Dart"), root, meta);
            }
            if (Files.exists(root.resolve("package.json"))) {
                meta.put("buildFile", "package.json");
                return new ProjectToolchain(BuildSystem.NPM, Set.of("JavaScript", "TypeScript"), root, meta);
            }
            if (Files.exists(root.resolve("Cargo.toml"))) {
                meta.put("buildFile", "Cargo.toml");
                return new ProjectToolchain(BuildSystem.CARGO, Set.of("Rust"), root, meta);
            }
            if (Files.exists(root.resolve("go.mod"))) {
                meta.put("buildFile", "go.mod");
                return new ProjectToolchain(BuildSystem.GO, Set.of("Go"), root, meta);
            }
            if (Files.exists(root.resolve("pyproject.toml")) || Files.exists(root.resolve("requirements.txt"))) {
                meta.put("buildFile", Files.exists(root.resolve("pyproject.toml")) ? "pyproject.toml" : "requirements.txt");
                return new ProjectToolchain(BuildSystem.PYTHON, Set.of("Python"), root, meta);
            }

            return ProjectToolchain.generic(root);
        });
    }

    private boolean hasXcodeProject(Path root) {
        if (Files.exists(root.resolve("Podfile")) || Files.exists(root.resolve("Cartfile"))) {
            return true;
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".xcodeproj") || name.endsWith(".xcworkspace");
            });
        } catch (IOException e) {
            return false;
        }
    }
}
