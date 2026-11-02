package tech.kayys.cifr.engine.patch;

import java.nio.file.Path;
import java.util.List;

public record FilePatch(
    Path path,
    String expectedContentHash,
    List<PatchHunk> hunks
) {
    public FilePatch {
        hunks = hunks != null ? List.copyOf(hunks) : List.of();
    }
}
