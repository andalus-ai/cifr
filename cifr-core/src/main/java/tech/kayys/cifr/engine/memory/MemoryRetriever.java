package tech.kayys.cifr.engine.memory;

import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * SPI for retrieving relevant memories given a structured query.
 */
public interface MemoryRetriever {

    Uni<List<MemoryMatch>> retrieve(MemoryQuery query);
}
