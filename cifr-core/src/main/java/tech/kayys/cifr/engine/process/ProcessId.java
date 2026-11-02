package tech.kayys.cifr.engine.process;

public record ProcessId(String value) {
    public static ProcessId of(String value) {
        return new ProcessId(value);
    }
}
