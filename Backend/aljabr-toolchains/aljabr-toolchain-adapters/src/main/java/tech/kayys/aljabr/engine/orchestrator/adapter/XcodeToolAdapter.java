package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class XcodeToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain != null && toolchain.buildSystem() == BuildSystem.XCODE;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();
            command.add("xcodebuild");

            Object scheme = intent.arguments().get("scheme");
            if (scheme != null && !scheme.toString().isBlank()) {
                command.add("-scheme");
                command.add(scheme.toString());
            }

            switch (intent.kind()) {
                case BUILD -> command.add("build");
                case RUN_TEST, RUN_TESTS -> {
                    command.add("test");
                    Object testTarget = intent.arguments().get("test");
                    if (testTarget != null && !testTarget.toString().isBlank()) {
                        command.add("-only-testing:" + testTarget);
                    }
                }
                case FORMAT -> {
                    command.clear();
                    Object file = intent.arguments().get("file");
                    String filePath = file != null ? file.toString() : "";
                    if (filePath.endsWith(".m") || filePath.endsWith(".h") || filePath.endsWith(".mm")) {
                        command.addAll(List.of("clang-format", "-i", filePath));
                    } else {
                        command.addAll(List.of("swift-format", "format", "-i", filePath.isBlank() ? "." : filePath));
                    }
                }
                case LINT -> {
                    command.clear();
                    command.add("swiftlint");
                }
                default -> {
                    Object rawCmd = intent.arguments().get("command");
                    if (rawCmd != null) {
                        return new ResolvedTool(intent.kind(), List.of(rawCmd.toString().split("\\s+")), toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
                    }
                    command.add("-showsdks");
                }
            }

            return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
        });
    }
}
