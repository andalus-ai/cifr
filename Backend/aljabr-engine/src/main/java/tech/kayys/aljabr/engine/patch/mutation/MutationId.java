package tech.kayys.aljabr.engine.patch.mutation;

import java.util.UUID;

public record MutationId(String value) {
    public static MutationId generate() {
        return new MutationId("mut-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
