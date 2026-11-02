package tech.kayys.cifr.engine.tool.diff;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DiffTool implements CifrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "diff",
            "Show differences between files, versions, or working copy.",
            ToolCategory.CODE_ANALYSIS,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "left", Map.of("type", "string", "description", "Left file or reference"),
                    "right", Map.of("type", "string", "description", "Right file or reference"),
                    "context", Map.of("type", "integer", "description", "Context lines (default: 3)"),
                    "ignoreWhitespace", Map.of("type", "boolean", "description", "Ignore whitespace changes")
                ),
                "required", List.of("left", "right")
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String leftStr = (String) request.arguments().get("left");
                String rightStr = (String) request.arguments().get("right");
                int contextLines = ((Number) request.arguments().getOrDefault("context", 3)).intValue();
                boolean ignoreWhitespace = (boolean) request.arguments().getOrDefault("ignoreWhitespace", false);

                Path leftPath = WORKSPACE_ROOT.resolve(leftStr).normalize();
                Path rightPath = WORKSPACE_ROOT.resolve(rightStr).normalize();

                if (!leftPath.startsWith(WORKSPACE_ROOT) || !rightPath.startsWith(WORKSPACE_ROOT)) {
                    return new DiffResult(false, "Access denied", List.of());
                }

                if (!Files.exists(leftPath)) {
                    return new DiffResult(false, "Left file does not exist: " + leftStr, List.of());
                }
                if (!Files.exists(rightPath)) {
                    return new DiffResult(false, "Right file does not exist: " + rightStr, List.of());
                }

                List<String> leftLines = Files.readAllLines(leftPath);
                List<String> rightLines = Files.readAllLines(rightPath);

                if (ignoreWhitespace) {
                    leftLines = leftLines.stream().map(String::trim).toList();
                    rightLines = rightLines.stream().map(String::trim).toList();
                }

                List<DiffBlock> blocks = computeDiff(leftLines, rightLines, contextLines);

                return new DiffResult(true, "Diff between " + leftStr + " and " + rightStr, blocks);

            } catch (Exception e) {
                return new DiffResult(false, "Error: " + e.getMessage(), List.of());
            }
        });
    }

    private List<DiffBlock> computeDiff(List<String> left, List<String> right, int context) {
        List<DiffBlock> blocks = new ArrayList<>();
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            if (left.get(i).equals(right.get(j))) {
                i++;
                j++;
                continue;
            }

            int startI = Math.max(0, i - context);
            int startJ = Math.max(0, j - context);

            int endI = i;
            int endJ = j;
            while (endI < left.size() && endJ < right.size() && 
                   !left.get(endI).equals(right.get(endJ))) {
                endI++;
                endJ++;
            }

            int finalEndI = Math.min(left.size(), endI + context);
            int finalEndJ = Math.min(right.size(), endJ + context);

            List<String> leftChanges = new ArrayList<>(left.subList(startI, finalEndI));
            List<String> rightChanges = new ArrayList<>(right.subList(startJ, finalEndJ));

            blocks.add(new DiffBlock(
                startI + 1, finalEndI,
                startJ + 1, finalEndJ,
                leftChanges,
                rightChanges
            ));

            i = endI;
            j = endJ;
        }

        return blocks;
    }
}
