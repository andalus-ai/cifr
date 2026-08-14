package tech.kayys.aljabr.engine.patch;

import java.util.List;

public record PatchOperation(
    String id,
    String path,
    List<PatchEdit> edits,
    String reason
) {
    public PatchOperation {
        edits = List.copyOf(edits);
    }
}
