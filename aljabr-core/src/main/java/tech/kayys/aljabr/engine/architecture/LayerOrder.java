package tech.kayys.aljabr.engine.architecture;

public enum LayerOrder {
    API(0),
    APPLICATION(1),
    DOMAIN(2),
    INFRASTRUCTURE(3);

    private final int level;

    LayerOrder(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
