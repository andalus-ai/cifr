package tech.kayys.aljabr.engine.tool.fs;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.List;

public record BatchPatchResult(boolean success, String message, List<PatchResult> patches, List<String> errors) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() {
        long ok = patches.stream().filter(PatchResult::success).count();
        return message + " (" + ok + "/" + patches.size() + " applied)";
    }
}
