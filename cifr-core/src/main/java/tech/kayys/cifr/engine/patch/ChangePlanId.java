package tech.kayys.cifr.engine.patch;

import java.util.UUID;

public record ChangePlanId(String value) {
    public static ChangePlanId generate() {
        return new ChangePlanId("cp-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
