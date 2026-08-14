package tech.kayys.wayang.gollek.cli.code;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.wayang.tools.spi.ToolContext;
import tech.kayys.wayang.tools.spi.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WayangRagAgentToolsReadFileToolTest {

    @TempDir
    Path workspace;

    @Test
    void readsDartPackageUriFromPackageConfig() throws Exception {
        Path packageRoot = workspace.resolve(".pub-cache/hosted/pub.dev/riverpod-2.6.1");
        Path element = packageRoot.resolve("lib/src/framework/element.dart");
        Files.createDirectories(element.getParent());
        Files.writeString(element, "class ProviderElementBase {}\nvoid notifyListeners() {}\n");

        Path packageConfig = workspace.resolve(".dart_tool/package_config.json");
        Files.createDirectories(packageConfig.getParent());
        Files.writeString(packageConfig, """
                {
                  "configVersion": 2,
                  "packages": [
                    {
                      "name": "riverpod",
                      "rootUri": "../.pub-cache/hosted/pub.dev/riverpod-2.6.1",
                      "packageUri": "lib/",
                      "languageVersion": "3.5"
                    }
                  ]
                }
                """);

        WayangRagAgentTools.ReadFileTool tool = new WayangRagAgentTools.ReadFileTool(workspace);
        ToolResult result = tool.execute(
                Map.of(
                        "path", "package:riverpod/src/framework/element.dart",
                        "start_line", 1,
                        "end_line", 1),
                ToolContext.defaults());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output -> assertThat(output)
                .contains("Resolved package:riverpod/src/framework/element.dart")
                .contains("package_config.json")
                .contains("1 | class ProviderElementBase {}"));
    }

    @Test
    void explainsHowToRecoverWhenDartPackageConfigIsMissing() {
        WayangRagAgentTools.ReadFileTool tool = new WayangRagAgentTools.ReadFileTool(workspace);
        ToolResult result = tool.execute(
                Map.of("path", "package:riverpod/src/framework/element.dart"),
                ToolContext.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.error())
                .contains("No .dart_tool/package_config.json")
                .contains("flutter pub get")
                .contains("package:riverpod/src/framework/element.dart");
    }
}
