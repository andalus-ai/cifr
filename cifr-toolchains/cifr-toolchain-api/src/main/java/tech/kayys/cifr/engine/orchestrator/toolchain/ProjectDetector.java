package tech.kayys.cifr.engine.orchestrator.toolchain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.orchestrator.adapter.ToolAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ProjectDetector {

    @Inject
    Instance<ToolAdapter> adapters;

    public ProjectToolchain detect(Path workspaceRoot) {
        Path root = workspaceRoot != null ? workspaceRoot : Path.of(".").toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return new ProjectToolchain(BuildSystem.UNKNOWN, TestFramework.UNKNOWN, Set.of(), List.of(), root);
        }

        BuildSystem buildSystem = BuildSystem.UNKNOWN;
        TestFramework testFramework = TestFramework.UNKNOWN;
        Set<String> languages = new HashSet<>();

        if (Files.exists(root.resolve("Package.swift"))) {
            buildSystem = BuildSystem.SWIFT_PM; languages.add("swift");
        } else if (hasXcodeProject(root)) {
            buildSystem = BuildSystem.XCODE; languages.add("swift"); languages.add("objective-c");
        } else if (Files.exists(root.resolve("CMakeLists.txt"))) {
            buildSystem = BuildSystem.CMAKE; languages.add("c"); languages.add("c++");
        } else if (Files.exists(root.resolve("meson.build"))) {
            buildSystem = BuildSystem.MESON; languages.add("c"); languages.add("c++");
        } else if (Files.exists(root.resolve("Makefile")) || Files.exists(root.resolve("makefile"))) {
            buildSystem = BuildSystem.MAKE;
        } else if (Files.exists(root.resolve("pom.xml"))) {
            buildSystem = BuildSystem.MAVEN; testFramework = TestFramework.JUNIT; languages.add("java");
        } else if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            buildSystem = BuildSystem.GRADLE; testFramework = TestFramework.JUNIT; languages.add("java"); languages.add("kotlin");
        } else if (Files.exists(root.resolve("pubspec.yaml"))) {
            buildSystem = BuildSystem.FLUTTER; languages.add("dart");
        } else if (Files.exists(root.resolve("package.json"))) {
            buildSystem = BuildSystem.NPM; testFramework = TestFramework.JEST; languages.add("javascript"); languages.add("typescript");
        } else if (Files.exists(root.resolve("Cargo.toml"))) {
            buildSystem = BuildSystem.CARGO; testFramework = TestFramework.CARGO_TEST; languages.add("rust");
        } else if (Files.exists(root.resolve("go.mod"))) {
            buildSystem = BuildSystem.GO_MODULE; testFramework = TestFramework.GO_TEST; languages.add("go");
        } else if (Files.exists(root.resolve("pyproject.toml")) || Files.exists(root.resolve("requirements.txt"))) {
            buildSystem = BuildSystem.PYPROJECT; testFramework = TestFramework.PYTEST; languages.add("python");
        }

        final BuildSystem detectedBuildSystem = buildSystem;
        List<ToolAdapter> supportedAdapters = new ArrayList<>();
        if (adapters != null) {
            for (ToolAdapter adapter : adapters) {
                ProjectToolchain probe = new ProjectToolchain(detectedBuildSystem, testFramework, languages, List.of(), root);
                if (adapter.supports(probe)) {
                    supportedAdapters.add(adapter);
                }
            }
        }

        return new ProjectToolchain(buildSystem, testFramework, Set.copyOf(languages), supportedAdapters, root);
    }

    private boolean hasXcodeProject(Path root) {
        if (Files.exists(root.resolve("Podfile")) || Files.exists(root.resolve("Cartfile"))) return true;
        try (var stream = Files.list(root)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".xcodeproj") || name.endsWith(".xcworkspace");
            });
        } catch (Exception e) {
            return false;
        }
    }
}
