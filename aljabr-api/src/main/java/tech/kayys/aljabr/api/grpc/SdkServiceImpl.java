package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.provider.ProviderDescriptor;
import tech.kayys.aljabr.engine.provider.ProviderRegistry;
import tech.kayys.andalus.inference.ModelInfo;
import tech.kayys.andalus.pro.api.grpc.Empty;
import tech.kayys.andalus.pro.api.grpc.ListModelsRequest;
import tech.kayys.andalus.pro.api.grpc.ListModelsResponse;
import tech.kayys.andalus.pro.api.grpc.ListProvidersResponse;
import tech.kayys.andalus.pro.api.grpc.MutinySdkServiceGrpc;
import tech.kayys.andalus.pro.api.grpc.SdkService;

import java.util.List;

/**
 * gRPC service that exposes provider and model discovery to the Flutter UI.
 *
 * <p>All data is sourced from {@link ProviderRegistry}, which auto-discovers every
 * {@link tech.kayys.andalus.inference.InferenceProvider} plugin on the classpath.
 * This class contains zero hardcoded provider or model names.
 */
@GrpcService
@jakarta.inject.Singleton
public class SdkServiceImpl extends MutinySdkServiceGrpc.SdkServiceImplBase implements SdkService {

    @Inject
    ProviderRegistry providerRegistry;

    @Override
    public Uni<ListProvidersResponse> listProviders(Empty request) {
        List<ProviderDescriptor> descriptors = providerRegistry.describe();

        ListProvidersResponse.Builder responseBuilder = ListProvidersResponse.newBuilder();
        for (ProviderDescriptor d : descriptors) {
            responseBuilder.addProviders(
                    tech.kayys.andalus.pro.api.grpc.ProviderInfo.newBuilder()
                            .setId(d.id())
                            .setName(d.name())
                            .setDescription(d.description() != null ? d.description() : "")
                            .build()
            );
        }

        return Uni.createFrom().item(responseBuilder.build());
    }

    @Override
    public Uni<ListModelsResponse> listModels(ListModelsRequest request) {
        String providerId = request.getProviderId();
        List<ModelInfo> models = providerRegistry.listModels(providerId);

        ListModelsResponse.Builder responseBuilder = ListModelsResponse.newBuilder();
        for (ModelInfo m : models) {
            String name = (m.name() != null && !m.name().isBlank() && !m.name().equalsIgnoreCase("unknown"))
                    ? m.name()
                    : m.id();
            String format = m.provider() != null ? m.provider() : "";
            long sizeBytes = 0L;
            if (m.metadata() != null && m.metadata().get("sizeBytes") instanceof Number n) {
                sizeBytes = n.longValue();
            }

            responseBuilder.addModels(
                    tech.kayys.andalus.pro.api.grpc.ModelInfo.newBuilder()
                            .setId(m.id())
                            .setName(name)
                            .setFormat(format)
                            .setSizeBytes(sizeBytes)
                            .build()
            );
        }

        return Uni.createFrom().item(responseBuilder.build());
    }
}
