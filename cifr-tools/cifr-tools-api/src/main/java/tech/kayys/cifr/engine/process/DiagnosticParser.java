package tech.kayys.cifr.engine.process;

import java.util.List;
import tech.kayys.cifr.engine.verify.Diagnostic;

public interface DiagnosticParser {

    boolean supports(String tool);

    List<Diagnostic> parse(String output);
}
