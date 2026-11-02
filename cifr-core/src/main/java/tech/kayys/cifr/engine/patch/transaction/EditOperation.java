package tech.kayys.cifr.engine.patch.transaction;

import java.nio.file.Path;

public sealed interface EditOperation {

    Path file();

    record ReplaceRange(
        Path file,
        int startLine,
        int endLine,
        String replacement
    ) implements EditOperation {}

    record InsertBefore(
        Path file,
        int line,
        String content
    ) implements EditOperation {}

    record InsertAfter(
        Path file,
        int line,
        String content
    ) implements EditOperation {}

    record DeleteRange(
        Path file,
        int startLine,
        int endLine
    ) implements EditOperation {}

    record AddImport(
        Path file,
        String importStatement
    ) implements EditOperation {}
}
