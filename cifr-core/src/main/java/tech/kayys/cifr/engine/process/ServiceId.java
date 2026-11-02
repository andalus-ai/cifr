package tech.kayys.cifr.engine.process;

public record ServiceId(String value) {
    public static ServiceId of(String value) {
        return new ServiceId(value);
    }
}
