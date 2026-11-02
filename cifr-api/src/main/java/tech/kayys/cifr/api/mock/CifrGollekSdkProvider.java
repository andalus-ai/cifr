package tech.kayys.cifr.api.mock;

import tech.kayys.gollek.sdk.config.SdkConfig;
import tech.kayys.gollek.sdk.core.GollekSdk;
import tech.kayys.gollek.sdk.core.GollekSdkProvider;
import tech.kayys.gollek.sdk.exception.SdkException;

/**
 * Service Provider implementation for GollekSdk in the Cifr runtime.
 * Provides the configured GollekSdk instance to GollekSdkFactory via ServiceLoader.
 */
public class CifrGollekSdkProvider implements GollekSdkProvider {

    private final MockGollekSdkProducer producer = new MockGollekSdkProducer();

    @Override
    public Mode mode() {
        return Mode.REMOTE;
    }

    @Override
    public GollekSdk create(SdkConfig config) throws SdkException {
        return producer.mockGollekSdk();
    }

    @Override
    public int priority() {
        return 1;
    }
}
