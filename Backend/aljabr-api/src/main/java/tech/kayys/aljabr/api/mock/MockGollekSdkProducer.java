package tech.kayys.aljabr.api.mock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.annotation.Priority;
import tech.kayys.gollek.sdk.core.GollekSdk;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class MockGollekSdkProducer {

    @Produces
    @ApplicationScoped
    @Alternative
    @Priority(1)
    public GollekSdk mockGollekSdk() {
        return (GollekSdk) Proxy.newProxyInstance(
            GollekSdk.class.getClassLoader(),
            new Class<?>[]{GollekSdk.class},
            (proxy, method, args) -> {
                String name = method.getName();
                Class<?> returnType = method.getReturnType();

                if ("toString".equals(name)) {
                    return "MockGollekSdk";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name) && args != null && args.length == 1) {
                    return proxy == args[0];
                }

                if (returnType.equals(List.class)) {
                    return Collections.emptyList();
                }
                if (returnType.equals(Set.class)) {
                    return Collections.emptySet();
                }
                if (returnType.equals(Map.class)) {
                    return Collections.emptyMap();
                }
                if (returnType.equals(Optional.class)) {
                    return Optional.empty();
                }
                if (returnType.equals(Uni.class)) {
                    return Uni.createFrom().nullItem();
                }
                if (returnType.equals(Multi.class)) {
                    return Multi.createFrom().empty();
                }
                if (returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
                    return Boolean.FALSE;
                }
                if (returnType.equals(int.class) || returnType.equals(Integer.class)) {
                    return 0;
                }
                if (returnType.equals(long.class) || returnType.equals(Long.class)) {
                    return 0L;
                }
                return null;
            }
        );
    }
}
