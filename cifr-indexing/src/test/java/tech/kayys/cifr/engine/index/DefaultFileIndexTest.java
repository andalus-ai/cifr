package tech.kayys.cifr.engine.index;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultFileIndexTest {
    @Test
    void indexesRelativePathsIndependentlyPerWorkspace() {
        DefaultFileIndex index = new DefaultFileIndex();
        FileRecord first = new FileRecord(Path.of("src/Main.java"), "a", 1, "java",
                false, false, FileRole.SOURCE, "workspace-a");
        FileRecord second = new FileRecord(Path.of("src/Main.java"), "b", 1, "java",
                false, false, FileRole.SOURCE, "workspace-b");

        index.registerFile(first);
        index.registerFile(second);

        List<FileRecord> firstWorkspace = index.getCurrentIndex("workspace-a").await().indefinitely();
        List<FileRecord> secondWorkspace = index.getCurrentIndex("workspace-b").await().indefinitely();

        assertEquals(List.of(first), firstWorkspace);
        assertEquals(List.of(second), secondWorkspace);
    }

    @Test
    void clearsOnlyTheRequestedWorkspace() {
        DefaultFileIndex index = new DefaultFileIndex();
        index.registerFile(new FileRecord(Path.of("src/Main.java"), "a", 1, "java",
                false, false, FileRole.SOURCE, "workspace-a"));
        index.registerFile(new FileRecord(Path.of("src/Main.java"), "b", 1, "java",
                false, false, FileRole.SOURCE, "workspace-b"));

        index.clearWorkspace("workspace-a");

        assertEquals(0, index.getCurrentIndex("workspace-a").await().indefinitely().size());
        assertEquals(1, index.getCurrentIndex("workspace-b").await().indefinitely().size());
    }
}
