package tech.kayys.cifr.engine.patch.mutation;

import java.nio.file.Path;
import java.util.List;

public record FileEdit(
    Path file,
    List<TextEdit> edits
) {
    public FileEdit {
        edits = edits != null ? List.copyOf(edits) : List.of();
    }
}
