package tech.kayys.aljabr.engine.patch.transaction;

import java.nio.file.Path;

/**
 * Checkpoint record of an individual file before mutation, storing original hash and content.
 */
public record FileCheckpoint(
    Path file,
    String contentHash,
    String originalContent
) {
    public static FileCheckpoint of(Path file, String contentHash, String originalContent) {
        return new FileCheckpoint(file, contentHash, originalContent);
    }
}
