package tech.kayys.aljabr.engine.skill.mutation;

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
