package tech.kayys.cifr.engine.phase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.api.agent.CodingRequest;
import tech.kayys.cifr.engine.repo.GitRepositoryService;
import tech.kayys.cifr.engine.repo.RepositorySnapshot;

import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultObserver implements Observer {
    private static final Logger log = Logger.getLogger(DefaultObserver.class.getName());

    @jakarta.inject.Inject
    GitRepositoryService gitService;

    @Override
    public Uni<ObservationResult> observe(CodingRequest request, ObservationContext context) {
        log.info("DefaultObserver: Observing repository context for request " + request.intent());
        
        RepositorySnapshot snapshot = gitService.captureSnapshot(context.state().getTask().workspace());
        
        ObservationResult result = new ObservationResult(
                snapshot,
                List.of(), // observations
                List.of(), // evidenceIds
                List.of()  // unknowns
        );
        
        return Uni.createFrom().item(result);
    }
}
