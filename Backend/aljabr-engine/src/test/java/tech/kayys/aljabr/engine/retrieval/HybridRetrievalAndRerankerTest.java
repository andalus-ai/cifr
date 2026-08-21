package tech.kayys.aljabr.engine.retrieval;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HybridRetrievalAndRerankerTest {

    private Retriever fileRetriever;
    private Retriever memoryRetriever;
    private CandidateDeduplicator deduplicator;
    private Reranker reranker;
    private DefaultRetrievalEngine retrievalEngine;

    @BeforeEach
    void setUp() {
        fileRetriever = mock(Retriever.class);
        when(fileRetriever.source()).thenReturn(CandidateSource.FILE);

        memoryRetriever = mock(Retriever.class);
        when(memoryRetriever.source()).thenReturn(CandidateSource.MEMORY);

        deduplicator = new DefaultCandidateDeduplicator();
        reranker = new HeuristicReranker();

        retrievalEngine = new DefaultRetrievalEngine(List.of(fileRetriever, memoryRetriever), deduplicator, reranker);
    }

    @Test
    void testCandidateDeduplicationAndReranking() {
        Candidate c1 = Candidate.of("1", CandidateSource.FILE, "class LoginService { void login() {} }", CandidateMetadata.of("LoginService", Set.of("auth"), 0.8));
        Candidate c2 = Candidate.of("1", CandidateSource.FILE, "class LoginService { void login() {} }", CandidateMetadata.of("LoginService", Set.of("auth"), 0.8));
        Candidate c3 = Candidate.of("2", CandidateSource.MEMORY, "Authentication resides in auth-core", CandidateMetadata.of("AuthMemory", Set.of("auth"), 0.9));

        CandidateSet set = CandidateSet.of(List.of(c1, c2, c3));
        CandidateSet deduped = deduplicator.deduplicate(set);

        assertEquals(2, deduped.size());

        RetrievalQuery query = RetrievalQuery.of("auth login", Set.of("auth"));
        List<RankedCandidate> ranked = reranker.rank(deduped, query);

        assertEquals(2, ranked.size());
        assertTrue(ranked.get(0).score() > 0.8);
    }
}
