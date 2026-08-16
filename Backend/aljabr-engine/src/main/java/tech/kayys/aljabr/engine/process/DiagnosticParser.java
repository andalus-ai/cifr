package tech.kayys.aljabr.engine.process;

import java.util.List;
import tech.kayys.aljabr.engine.verify.Diagnostic;

public interface DiagnosticParser {

    boolean supports(String tool);

    List<Diagnostic> parse(String output);
}
