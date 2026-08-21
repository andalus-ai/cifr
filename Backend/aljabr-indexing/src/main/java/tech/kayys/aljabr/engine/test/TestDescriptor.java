package tech.kayys.aljabr.engine.test;

import java.util.List;

public record TestDescriptor(
    String id,
    String name,
    TestType type,
    List<TestMethod> methods
) {
    public TestDescriptor {
        methods = methods != null ? List.copyOf(methods) : List.of();
    }
}
