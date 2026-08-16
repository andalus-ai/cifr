package tech.kayys.aljabr.engine.process;

import java.util.UUID;

public record ServiceId(String value) {
    public static ServiceId generate() {
        return new ServiceId("srv-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
