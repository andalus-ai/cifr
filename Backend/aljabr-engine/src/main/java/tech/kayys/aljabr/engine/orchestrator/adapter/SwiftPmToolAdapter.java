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
public class SwiftPmToolAdapter implements ToolAdapter {

    @Override
    public boolean supports(ProjectToolchain toolchain) {
        return toolchain != null && toolchain.buildSystem() == BuildSystem.SWIFT_PM;
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        return Uni.createFrom().item(() -> {
            List<String> command = new ArrayList<>();

            switch (intent.kind()) {
                case BUILD -> {
                    command.add("swift");
                    command.add("build");
                }
                case RUN_TEST -> {
                    command.add("swift");
                    command.add("test");
                    Object testTarget = intent.arguments().get("test");
                    if (testTarget != null && !testTarget.toString().isBlank()) {
                        command.add("--filter");
                        command.add(testTarget.toString());
                    }
                }
                case RUN_TESTS -> {
                    command.add("swift");
                    command.add("test");
                }
                case FORMAT -> {
                    command.add("swift-format");
                    command.add("format");
                    command.add("-i");
                    Object file = intent.arguments().get("file");
                    command.add(file != null ? file.toString() : "Sources");
                }
                case LINT -> {
                    command.add("swiftlint");
                    Object file = intent.arguments().get("file");
                    if (file != null) {
                        command.add(file.toString());
                    }
                }
                default -> {
                    Object rawCmd = intent.arguments().get("command");
                    if (rawCmd != null) {
                        return new ResolvedTool(intent.kind(), List.of(rawCmd.toString().split("\\s+")), toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
                    }
                    command.add("swift");
                    command.add("package");
                    command.add("describe");
                }
            }

            return new ResolvedTool(intent.kind(), command, toolchain.rootDirectory(), ExecutionEnvironment.defaultSandboxed());
        });
    }
}
