package tech.kayys.cifr.engine.patch.mutation;

import java.util.Optional;

public record MutationAuthorization(
    Status status,
    Optional<String> reason
) {
    public enum Status {
        ALLOW,
        DENY,
        REQUIRE_USER
    }

    public static MutationAuthorization allow() {
        return new MutationAuthorization(Status.ALLOW, Optional.empty());
    }

    public static MutationAuthorization deny(String reason) {
        return new MutationAuthorization(Status.DENY, Optional.of(reason));
    }

    public static MutationAuthorization requireUser(String reason) {
        return new MutationAuthorization(Status.REQUIRE_USER, Optional.of(reason));
    }
}
