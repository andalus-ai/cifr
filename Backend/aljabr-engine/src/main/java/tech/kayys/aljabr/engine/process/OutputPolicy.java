package tech.kayys.aljabr.engine.process;

public record OutputPolicy(
    long maxBytes,
    int maxLines,
    boolean preserveFullArtifact,
    boolean streamLive
) {
    public static OutputPolicy defaultPolicy() {
        return new OutputPolicy(1024 * 1024, 2000, true, false);
    }
}
