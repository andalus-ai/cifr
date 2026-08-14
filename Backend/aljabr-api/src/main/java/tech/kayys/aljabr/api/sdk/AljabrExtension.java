package tech.kayys.aljabr.api.sdk;

import tech.kayys.wayang.pro.sdk.client.WayangProClient;
import tech.kayys.wayang.pro.sdk.client.WayangProExtension;

/**
 * Aljabr SDK Extension Plugin.
 * Plugs into the WayangProClient to expose Aljabr-specific APIs (LSP,
 * sandboxing, advanced code gen).
 */
public class AljabrExtension implements WayangProExtension {

    private WayangProClient coreClient;

    @Override
    public void init(WayangProClient client) {
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
