package tech.kayys.aljabr.api.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.aljabr.engine.provider.ProviderDescriptor;
import tech.kayys.aljabr.engine.provider.ProviderRegistry;
import tech.kayys.andalus.inference.ModelInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint exposing registered {@link tech.kayys.andalus.inference.InferenceProvider}
 * plugins and their supported models.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ProviderResource {

    @Inject
    ProviderRegistry providerRegistry;

    @GET
    @Path("/providers")
    public Response listProviders() {
        List<ProviderDescriptor> descriptors = providerRegistry.describe();
        List<Map<String, Object>> response = new ArrayList<>();
        for (ProviderDescriptor d : descriptors) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.id());
            item.put("name", d.name());
            item.put("description", d.description() != null ? d.description() : "");
            item.put("models", d.models() != null ? d.models() : List.of());
            response.add(item);
        }
        return Response.ok(response).build();
    }

    @GET
    @Path("/models")
    public Response listModels(@QueryParam("provider") String providerId) {
        String pid = (providerId == null || providerId.isBlank()) ? "gollek" : providerId;
        List<ModelInfo> models = providerRegistry.listModels(pid);
        List<Map<String, Object>> response = new ArrayList<>();
        for (ModelInfo m : models) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.id());
            item.put("name", m.name() != null ? m.name() : m.id());
            item.put("format", m.provider() != null ? m.provider() : "");
            item.put("capabilities", m.capabilities() != null ? m.capabilities() : List.of());
            response.add(item);
        }
        return Response.ok(response).build();
    }
}
