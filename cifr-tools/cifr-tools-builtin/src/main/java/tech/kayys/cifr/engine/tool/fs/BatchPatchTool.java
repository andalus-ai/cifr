package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies targeted text patches to multiple files atomically.
 * Each patch specifies an exact match string to find and a replacement.
 * All patches are validated first, then applied as a unit with backup/rollback.
 */
@ApplicationScoped
public class BatchPatchTool implements CifrTool {
    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "batch_patch",
            "Apply targeted text patches to multiple files atomically with rollback on failure.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "patches", Map.of("type", "array", "description",
                        "List of {path, find, replace, isRegex, all} patch specs"),
                    "atomic", Map.of("type", "boolean", "description", "Roll back all on any failure")
                ),
                "required", List.of("patches")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_WORKSPACE),
            ToolRisk.HIGH
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> patchSpecs = (List<Map<String, Object>>) request.arguments().get("patches");
            boolean atomic = !Boolean.FALSE.equals(request.arguments().getOrDefault("atomic", true));

            if (patchSpecs == null || patchSpecs.isEmpty())
                return new BatchPatchResult(false, "No patches specified", List.of(), List.of());

            Map<Path, String> backups = new LinkedHashMap<>();
            List<PatchResult> applied = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            try {
                // Phase 1: Validate and backup
                for (Map<String, Object> spec : patchSpecs) {
                    String pathStr = (String) spec.get("path");
                    if (pathStr == null) throw new IllegalArgumentException("Missing path in patch spec");
                    Path abs = context.resolveWorkspacePath(pathStr);
                    if (!Files.exists(abs)) throw new NoSuchFileException("File not found: " + pathStr);
                    if (!backups.containsKey(abs)) backups.put(abs, Files.readString(abs));
                }

                // Phase 2: Apply
                for (Map<String, Object> spec : patchSpecs) {
                    String pathStr = (String) spec.get("path");
                    String find = (String) spec.get("find");
                    String replace = (String) spec.getOrDefault("replace", "");
                    boolean isRegex = Boolean.TRUE.equals(spec.get("isRegex"));
                    boolean all = !Boolean.FALSE.equals(spec.getOrDefault("all", true));

                    Path abs = context.resolveWorkspacePath(pathStr);
                    String content = Files.readString(abs);
                    String newContent;
                    int matchCount;

                    if (isRegex) {
                        Pattern p = Pattern.compile(find, Pattern.DOTALL);
                        Matcher m = p.matcher(content);
                        matchCount = (int) m.results().count();
                        m.reset();
                        newContent = all ? m.replaceAll(replace) : m.replaceFirst(replace);
                    } else {
                        matchCount = countOccurrences(content, find);
                        newContent = all ? content.replace(find, replace) : replaceFirst(content, find, replace);
                    }

                    if (matchCount == 0) {
                        errors.add("No match for find pattern in: " + pathStr);
                        if (atomic) break;
                        applied.add(new PatchResult(pathStr, false, "Pattern not found", 0));
                        continue;
                    }

                    Files.writeString(abs, newContent);
                    applied.add(new PatchResult(pathStr, true, "OK", matchCount));
                }

                if (!errors.isEmpty() && atomic) throw new IllegalStateException("Match failures: " + errors);

                String msg = "Applied " + applied.size() + " patch(es)" + (errors.isEmpty() ? "" : ", " + errors.size() + " warnings");
                return new BatchPatchResult(errors.isEmpty(), msg, applied, errors);

            } catch (Exception e) {
                if (atomic) {
                    for (Map.Entry<Path, String> entry : backups.entrySet()) {
                        try { Files.writeString(entry.getKey(), entry.getValue()); } catch (IOException ignored) {}
                    }
                }
                return new BatchPatchResult(false, "Patch failed and rolled back: " + e.getMessage(), applied, errors);
            }
        });
    }

    private int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }

    private String replaceFirst(String text, String find, String replace) {
        int idx = text.indexOf(find);
        if (idx < 0) return text;
        return text.substring(0, idx) + replace + text.substring(idx + find.length());
    }
}
