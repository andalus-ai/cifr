package tech.kayys.aljabr.skill;

import tech.kayys.wayang.tool.ToolResult;
import java.util.Map;
import java.util.HashMap;

public class AljabrToolResult implements ToolResult {
    private final Map<String, Object> outputs;
    private final boolean success;
    private final String errorMessage;

    public AljabrToolResult(Map<String, Object> outputs, boolean success, String errorMessage) {
        this.outputs = outputs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static AljabrToolResult success(String output) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        map.put("content", output);
        return new AljabrToolResult(map, true, null);
    }
    
    public static AljabrToolResult successMap(Map<String, Object> outputs) {
        return new AljabrToolResult(outputs, true, null);
    }

    public static AljabrToolResult error(String errorMessage) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "error");
        map.put("message", errorMessage);
        return new AljabrToolResult(map, false, errorMessage);
    }

    @Override
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public tech.kayys.wayang.identity.ResourceId id() {
        return tech.kayys.wayang.identity.ResourceId.from(tech.kayys.wayang.extension.Id.random(), new tech.kayys.wayang.resource.ResourceType.Custom("tool-result"));
    }

    @Override
    public tech.kayys.wayang.resource.ResourceType type() {
        return new tech.kayys.wayang.resource.ResourceType.Custom("tool-result");
    }

    @Override
    public tech.kayys.wayang.extension.Metadata metadata() {
        return tech.kayys.wayang.extension.Metadata.builder().build();
    }
}
