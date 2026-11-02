package tech.kayys.cifr.engine.provider;

import java.util.List;

/**
 * A serialisable summary of a registered {@link tech.kayys.andalus.inference.InferenceProvider}.
 *
 * <p>This record is the canonical transfer object between the engine layer and the
 * gRPC API layer, keeping each layer free of the other's types.
 */
public record ProviderDescriptor(
        String id,
        String name,
        String description,
        List<String> models
) {
}
