package tech.kayys.aljabr.engine.context;

import java.util.List;

/**
 * Incremental change delta applied to an active ContextPacket to avoid full rebuilds.
 */
public record ContextDelta(
    List<ContextItem> added,
    List<String> removed,
    List<ContextItem> replaced
) {
    public ContextDelta {
        added = added != null ? List.copyOf(added) : List.of();
        removed = removed != null ? List.copyOf(removed) : List.of();
        replaced = replaced != null ? List.copyOf(replaced) : List.of();
    }

    public static ContextDelta empty() {
        return new ContextDelta(List.of(), List.of(), List.of());
    }
}
