package tech.kayys.aljabr.engine.retrieval;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Default multi-source retrieval engine coordinating individual source retrievers and deduplication.
 */
@ApplicationScoped
public class DefaultRetrievalEngine implements RetrievalEngine {

    @Inject
    Instance<Retriever> retrievers;

    @Inject
    CandidateDeduplicator deduplicator;

    @Inject
    Reranker reranker;

    public DefaultRetrievalEngine() {}

    public DefaultRetrievalEngine(List<Retriever> retrieverList, CandidateDeduplicator deduplicator, Reranker reranker) {
        this.deduplicator = deduplicator;
        this.reranker = reranker;
    }

    @Override
    public Uni<CandidateSet> retrieve(RetrievalQuery query) {
        if (query == null) {
            return Uni.createFrom().item(CandidateSet.empty());
        }

        List<Uni<CandidateSet>> futures = new ArrayList<>();
        if (retrievers != null) {
            for (Retriever r : retrievers) {
                futures.add(r.retrieve(query));
            }
        }

        if (futures.isEmpty()) {
            return Uni.createFrom().item(CandidateSet.empty());
        }

        return Uni.combine().all().unis(futures)
            .with(results -> {
                List<Candidate> all = new ArrayList<>();
                for (Object res : results) {
                    if (res instanceof CandidateSet cs) {
                        all.addAll(cs.candidates());
                    }
                }
                CandidateSet raw = CandidateSet.of(all);
                CandidateSet deduped = deduplicator.deduplicate(raw);

                List<RankedCandidate> ranked = reranker.rank(deduped, query);
                int limit = query.budget() != null ? query.budget().maxCandidates() : 30;

                List<Candidate> top = ranked.stream()
                    .limit(limit)
                    .map(RankedCandidate::candidate)
                    .toList();

                return CandidateSet.of(top);
            });
    }
}
