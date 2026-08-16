package tech.kayys.aljabr.engine.debug;

import tech.kayys.aljabr.engine.exec.ExecutionEvidence;

public interface FailureClassifier {

    FailureClassification classify(ExecutionEvidence evidence);
}
