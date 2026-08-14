package tech.kayys.wayang.gollek.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.sdk.gollek.tools.ToolsFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WayangCodeFallbackToolsFactoryTest {

    @TempDir
    Path workspace;

    @Test
    void providesMinimalScannerGrepPlannerAndTaskStore() throws Exception {
        Path source = workspace.resolve("Example.java");
        Files.writeString(source, "class Example {\n  // TODO inspect\n}\n");
        ToolsFactory tools = WayangCodeFallbackToolsFactory.create();

        var files = tools.createScanner(workspace).findFiles("*.java");
        var matches = tools.createGrep(workspace).grep("TODO", files);
        var plan = tools.createPlanner().makePlan("harden code command");
        var task = tools.createTaskStore(workspace).addTask("write test");

        assertThat(files).containsExactly(source.toAbsolutePath().normalize());
        assertThat(matches)
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.file).isEqualTo(source.toAbsolutePath().normalize());
                    assertThat(match.lineNumber).isEqualTo(2);
                    assertThat(match.line).contains("TODO");
                });
        assertThat(plan)
                .extracting(step -> step.text)
                .anySatisfy(text -> assertThat(text).contains("harden code command"));
        assertThat(task.id()).isNotBlank();
        assertThat(task.description()).isEqualTo("write test");
    }
}
