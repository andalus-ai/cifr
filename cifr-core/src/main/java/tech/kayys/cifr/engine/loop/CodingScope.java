package tech.kayys.cifr.engine.loop;

import java.util.List;

public record CodingScope(
    List<String> targetFiles,
    List<String> relatedModules
) {
    public static CodingScope empty() {
        return new CodingScope(List.of(), List.of());
    }
}
