package tech.kayys.cifr.engine.process;

import java.util.List;
import tech.kayys.cifr.engine.verify.Diagnostic;

public interface OutputNormalizer {

    List<Diagnostic> normalize(String tool, String rawOutput);

    String summarize(String tool, String rawOutput, int maxLines);
}
