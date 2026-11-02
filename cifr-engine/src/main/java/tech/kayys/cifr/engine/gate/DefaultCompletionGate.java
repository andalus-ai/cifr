package tech.kayys.cifr.engine.gate;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.loop.CodingTask;
import tech.kayys.cifr.engine.phase.TaskExecutionResult;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultCompletionGate implements CompletionGate {
    private static final Logger log = Logger.getLogger(DefaultCompletionGate.class.getName());

    @Override
    public Uni<CompletionDecision> evaluate(CodingTask task, TaskExecutionResult result, CodingExecutionContext context) {
        log.info("DefaultCompletionGate: Evaluating claim " + result.claim());
        
        // Simplified default implementation
        return Uni.createFrom().item(new CompletionDecision(true, "Claim accepted by default gate"));
    }
}
