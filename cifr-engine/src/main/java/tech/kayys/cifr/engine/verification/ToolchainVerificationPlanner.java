package tech.kayys.cifr.engine.verification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.cifr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectDetector;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectToolchain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

/**
 * Builds a small, deterministic verification plan from repository markers.
 *
 * <p>This component does not execute commands and does not accept commands from
 * model output. Execution remains a separate policy-controlled operation; in
 * particular, build scripts must only run in an appropriately isolated backend.</p>
 */
@ApplicationScoped
public class ToolchainVerificationPlanner {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ProjectDetector projectDetector;

    @Inject
    public ToolchainVerificationPlanner(ProjectDetector projectDetector) {
        this.projectDetector = projectDetector;
    }

    public VerificationPlan plan(Path workspaceRoot) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot must not be null");
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("workspaceRoot must be an existing directory: " + root);
        }

        ProjectToolchain detected = projectDetector.detect(root);
        BuildSystem buildSystem = detected.buildSystem();
        List<VerificationCheckDefinition> checks = switch (buildSystem) {
            case MAVEN -> mavenChecks(root);
            case GRADLE -> gradleChecks(root);
            case NPM -> nodeChecks(root);
            case CARGO -> cargoChecks();
            case GO_MODULE, GO -> goChecks();
            case PYPROJECT, PYTHON -> pythonChecks();
            case SWIFT_PM -> swiftChecks();
            case FLUTTER -> flutterChecks();
            default -> List.of();
        };

        String reason = checks.isEmpty()
                ? "No trusted compile/test command mapping is configured for build system " + buildSystem
                : "";
        return new VerificationPlan(buildSystem, root, checks, reason);
    }

    private static List<VerificationCheckDefinition> mavenChecks(Path root) {
        String launcher = wrapperOrSystem(root, "mvnw", "mvn");
        return List.of(
                check("compile", "maven", launcher, "-B", "-DskipTests", "compile"),
                check("tests", "maven", launcher, "-B", "test"));
    }

    private static List<VerificationCheckDefinition> gradleChecks(Path root) {
        String launcher = wrapperOrSystem(root, "gradlew", "gradle");
        return List.of(
                check("compile", "gradle", launcher, "classes"),
                check("tests", "gradle", launcher, "test"));
    }

    private static List<VerificationCheckDefinition> nodeChecks(Path root) {
        Path packageJson = root.resolve("package.json");
        try {
            JsonNode document = JSON.readTree(packageJson.toFile());
            JsonNode scripts = document == null ? null : document.path("scripts");
            if (scripts == null || !scripts.isObject()) {
                return List.of();
            }
            java.util.ArrayList<VerificationCheckDefinition> checks = new java.util.ArrayList<>();
            if (isConfiguredScript(scripts, "build")) {
                checks.add(check("compile", "npm", "npm", "run", "build"));
            }
            if (isConfiguredScript(scripts, "test")) {
                checks.add(check("tests", "npm", "npm", "test"));
            }
            return List.copyOf(checks);
        } catch (IOException | RuntimeException invalidPackageManifest) {
            return List.of();
        }
    }

    private static boolean isConfiguredScript(JsonNode scripts, String name) {
        JsonNode script = scripts.get(name);
        return script != null && script.isTextual() && !script.asText().isBlank();
    }

    private static List<VerificationCheckDefinition> cargoChecks() {
        return List.of(
                check("compile", "cargo", "cargo", "check", "--workspace"),
                check("tests", "cargo", "cargo", "test", "--workspace"));
    }

    private static List<VerificationCheckDefinition> goChecks() {
        return List.of(
                check("compile", "go", "go", "build", "./..."),
                check("tests", "go", "go", "test", "./..."));
    }

    private static List<VerificationCheckDefinition> pythonChecks() {
        return List.of(
                check("compile", "python", "python", "-m", "compileall", "-q", "."),
                check("tests", "pytest", "python", "-m", "pytest"));
    }

    private static List<VerificationCheckDefinition> swiftChecks() {
        return List.of(
                check("compile", "swift", "swift", "build"),
                check("tests", "swift", "swift", "test"));
    }

    private static List<VerificationCheckDefinition> flutterChecks() {
        return List.of(
                check("compile", "flutter", "flutter", "analyze"),
                check("tests", "flutter", "flutter", "test"));
    }

    private static String wrapperOrSystem(Path root, String wrapper, String systemCommand) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return !windows && Files.isRegularFile(root.resolve(wrapper))
                ? "./" + wrapper
                : systemCommand;
    }

    private static VerificationCheckDefinition check(
            String checkId, String verifierId, String... command) {
        return new VerificationCheckDefinition(checkId, verifierId, List.of(command));
    }
}
