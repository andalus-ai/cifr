package tech.kayys.aljabr.engine.index.evidence;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface EvidenceGraph {

    Uni<List<String>> findConnectedEvidence(String symbolOrFile);

    Uni<List<EvidenceScore>> rankEvidence(List<String> evidenceIds);

    Uni<RepositoryEvidencePacket> buildPacket(List<String> evidenceIds);
}
