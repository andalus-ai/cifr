package tech.kayys.aljabr.engine.process;

import java.util.List;
import tech.kayys.aljabr.engine.verify.Diagnostic;

public interface OutputNormalizer {

    List<Diagnostic> normalize(String tool, String rawOutput);

    String summarize(String tool, String rawOutput, int maxLines);
}
