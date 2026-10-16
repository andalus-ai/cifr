package tech.kayys.aljabr.engine.retrieval;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic scoring based on keyword overlap, entity matches, and confidence.
 */
@ApplicationScoped
public class HeuristicReranker implements Reranker {

    @Override
    public List<RankedCandidate> rank(CandidateSet candidates, RetrievalQuery query) {
        if (candidates == null || candidates.candidates().isEmpty()) {
            return List.of();
        }

        List<RankedCandidate> ranked = new ArrayList<>();
        String lowerObj = query != null && query.objective() != null ? query.objective().toLowerCase() : "";

        for (Candidate c : candidates.candidates()) {
            double score = c.metadata().confidence();
            List<String> reasons = new ArrayList<>();

            if (!lowerObj.isEmpty() && c.content().toLowerCase().contains(lowerObj)) {
                score += 0.5;
                reasons.add("OBJECTIVE_MATCH");
            }

            if (query != null) {
                for (String entity : query.entities()) {
                    if (c.metadata().entities().contains(entity) || c.content().contains(entity)) {
                        score += 0.3;
                        reasons.add("ENTITY_MATCH:" + entity);
                    }
                }
            }

            ranked.add(RankedCandidate.of(c, score, reasons));
        }

        ranked.sort(Comparator.comparingDouble(RankedCandidate::score).reversed());
        return ranked;
    }
}
