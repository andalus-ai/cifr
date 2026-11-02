package tech.kayys.cifr.engine.debug;

import java.util.UUID;

public record DebugSessionId(String value) {
    public static DebugSessionId generate() {
        return new DebugSessionId("dbg-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
