package tech.kayys.aljabr.api.sdk;

import tech.kayys.andalus.pro.sdk.client.AndalusProClient;
import tech.kayys.andalus.pro.sdk.client.AndalusProExtension;

/**
 * Aljabr SDK Extension Plugin.
 * Plugs into the AndalusProClient to expose Aljabr-specific APIs (LSP,
 * sandboxing, advanced code gen).
 */
public class AljabrExtension implements AndalusProExtension {

    private AndalusProClient coreClient;

    @Override
    public void init(AndalusProClient client) {
        this.coreClient = client;
    }

    /**
     * Submit an LSP diagnostic payload to the Aljabr agent context.
     * (Stub implementation for architecture proof)
     */
    public void submitDiagnostic(String fileUri, String diagnosticJson) {
        // Here Aljabr would route the diagnostic into the core client's context
        System.out.println("Aljabr: Submitted diagnostic for " + fileUri);
    }
}
