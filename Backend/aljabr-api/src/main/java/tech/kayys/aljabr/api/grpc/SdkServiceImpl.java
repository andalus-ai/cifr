package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import tech.kayys.wayang.pro.api.grpc.*;

@GrpcService
@jakarta.inject.Singleton
public class SdkServiceImpl extends MutinySdkServiceGrpc.SdkServiceImplBase implements SdkService {

    @Override
    public Uni<ListProvidersResponse> listProviders(Empty request) {
        return Uni.createFrom().item(ListProvidersResponse.newBuilder()
                .addProviders(ProviderInfo.newBuilder()
                        .setId("gollek")
                        .setName("Gollek Local")
                        .setDescription("Local LLM runner")
                        .build())
                .build());
    }

    @Override
    public Uni<ListModelsResponse> listModels(ListModelsRequest request) {
        return Uni.createFrom().item(ListModelsResponse.newBuilder()
                .addModels(ModelInfo.newBuilder()
                        .setId("hf:unsloth/gemma-4-12b-it-GGUF")
                        .setName("gemma-4-12b-it-GGUF")
                        .setFormat("gguf")
                        .build())
                .build());
    }
}
