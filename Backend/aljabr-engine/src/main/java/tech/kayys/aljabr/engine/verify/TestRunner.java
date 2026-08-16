package tech.kayys.aljabr.engine.verify;

import io.smallrye.mutiny.Uni;
import java.util.List;
import tech.kayys.aljabr.engine.index.test.TestCandidate;

public interface TestRunner {

    Uni<TestResult> runTest(TestCandidate testCandidate);

    Uni<List<TestResult>> runTests(List<TestCandidate> testCandidates);
}
