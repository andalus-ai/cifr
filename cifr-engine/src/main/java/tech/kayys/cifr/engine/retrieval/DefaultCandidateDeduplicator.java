package tech.kayys.cifr.engine.retrieval;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deduplicates candidates by content hash and entity overlap.
 */
@ApplicationScoped
public class DefaultCandidateDeduplicator implements CandidateDeduplicator {

    @Override
    public CandidateSet deduplicate(CandidateSet candidates) {
        if (candidates == null || candidates.candidates().isEmpty()) {
            return CandidateSet.empty();
        }

        Set<String> seenHashes = new HashSet<>();
        List<Candidate> unique = new ArrayList<>();

        for (Candidate c : candidates.candidates()) {
            String hash = c.source().name() + ":" + c.id() + ":" + c.content().trim().hashCode();
            if (seenHashes.add(hash)) {
                unique.add(c);
            }
        }

        return CandidateSet.of(unique);
    }
}
