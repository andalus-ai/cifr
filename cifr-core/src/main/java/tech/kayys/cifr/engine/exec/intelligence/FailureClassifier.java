package tech.kayys.cifr.engine.exec.intelligence;

public interface FailureClassifier {

    FailureAnalysis classify(NormalizedOutput output);
}
