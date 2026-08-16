package tech.kayys.aljabr.engine.patch;

import java.util.UUID;

public record ChangePlanId(String value) {
    public static ChangePlanId generate() {
        return new ChangePlanId("cp-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
