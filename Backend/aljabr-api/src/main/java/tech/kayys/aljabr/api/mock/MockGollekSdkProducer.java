package tech.kayys.aljabr.api.mock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.annotation.Priority;
import tech.kayys.gollek.sdk.core.GollekSdk;
import java.lang.reflect.Proxy;

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
                if (method.getName().equals("toString")) return "MockGollekSdk";
                return null;
            }
        );
    }
}
