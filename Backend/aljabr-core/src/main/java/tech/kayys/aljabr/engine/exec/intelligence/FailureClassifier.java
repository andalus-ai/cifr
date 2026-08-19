package tech.kayys.aljabr.engine.exec.intelligence;

public interface FailureClassifier {

    FailureAnalysis classify(NormalizedOutput output);
}
