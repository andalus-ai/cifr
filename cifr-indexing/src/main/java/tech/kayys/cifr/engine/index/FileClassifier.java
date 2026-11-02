package tech.kayys.cifr.engine.index;

import java.nio.file.Path;

public interface FileClassifier {
    FileRole classifyRole(Path path);
    String detectLanguage(Path path);
    boolean isGenerated(Path path);
    boolean isTest(Path path);
}
