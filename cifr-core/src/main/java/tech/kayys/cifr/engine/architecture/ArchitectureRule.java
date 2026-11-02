package tech.kayys.cifr.engine.architecture;

public record ArchitectureRule(
    LayerOrder fromLayer,
    LayerOrder toLayer,
    boolean allowed,
    String description
) {
    public static ArchitectureRule disallow(LayerOrder from, LayerOrder to, String reason) {
        return new ArchitectureRule(from, to, false, reason);
    }

    public static ArchitectureRule allow(LayerOrder from, LayerOrder to, String reason) {
        return new ArchitectureRule(from, to, true, reason);
    }
}
