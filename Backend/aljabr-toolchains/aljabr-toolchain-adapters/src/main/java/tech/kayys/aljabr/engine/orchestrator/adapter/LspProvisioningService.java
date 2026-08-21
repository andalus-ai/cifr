package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Auto-provisions Language Server Protocol (LSP) and Debug Adapter Protocol (DAP)
 * binaries from the Open VSX registry on-demand for active workspace languages.
 */
@ApplicationScoped
public class LspProvisioningService {

    private static final Logger LOG = Logger.getLogger(LspProvisioningService.class);
    private static final String DEFAULT_REGISTRY_URL = "https://open-vsx.org/api";
    private static final Path CACHE_ROOT = Paths.get(System.getProperty("user.home"), ".aljabr", "lsp-cache");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final Map<String, LspDefinition> knownServers = new ConcurrentHashMap<>();

    public record LspDefinition(
        String language,
        String publisher,
        String extensionName,
        String version,
        String binaryRelPath
    ) {}

    public LspProvisioningService() {
        registerDefaultServers();
    }

    private void registerDefaultServers() {
        // Java LSP (Eclipse JDT.LS via RedHat Java)
        knownServers.put("java", new LspDefinition("java", "redhat", "java", "1.38.0", "extension/server"));
        // Python LSP (Pyright)
        knownServers.put("python", new LspDefinition("python", "ms-pyright", "pyright", "1.1.390", "extension/dist/pyright-langserver.js"));
        // Go LSP (Gopls)
        knownServers.put("go", new LspDefinition("go", "golang", "go", "0.45.0", "extension/bin/gopls"));
        // Rust LSP (Rust-Analyzer)
        knownServers.put("rust", new LspDefinition("rust", "rust-lang", "rust-analyzer", "0.4.2250", "extension/server/rust-analyzer"));
        // TypeScript / JavaScript
        knownServers.put("typescript", new LspDefinition("typescript", "typescript-language-server", "typescript-language-server", "4.3.3", "extension/bin/typescript-language-server"));
        knownServers.put("javascript", new LspDefinition("javascript", "typescript-language-server", "typescript-language-server", "4.3.3", "extension/bin/typescript-language-server"));
    }

    public Uni<Path> ensureLspInstalled(String language) {
        LspDefinition def = knownServers.get(language.toLowerCase());
        if (def == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("No known LSP provider for language: " + language));
        }

        Path targetDir = CACHE_ROOT.resolve(def.publisher()).resolve(def.extensionName()).resolve(def.version());
        if (Files.exists(targetDir)) {
            LOG.infof("LSP for %s is already cached at %s", language, targetDir);
            return Uni.createFrom().item(targetDir);
        }

        return Uni.createFrom().item(() -> {
            try {
                Files.createDirectories(targetDir);
                LOG.infof("Auto-downloading LSP extension %s/%s:%s for %s...", def.publisher(), def.extensionName(), def.version(), language);

                String downloadUrl = String.format("%s/%s/%s/%s/file/%s.%s-%s.vsix",
                    DEFAULT_REGISTRY_URL, def.publisher(), def.extensionName(), def.version(),
                    def.publisher(), def.extensionName(), def.version());

                Path tempVsix = Files.createTempFile("aljabr-lsp-" + def.extensionName(), ".vsix");
                HttpResponse<Path> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(downloadUrl)).GET().timeout(Duration.ofMinutes(3)).build(),
                    HttpResponse.BodyHandlers.ofFile(tempVsix)
                );

                if (response.statusCode() != 200) {
                    throw new IOException("Failed to download LSP vsix from " + downloadUrl + " - status: " + response.statusCode());
                }

                // Extract vsix
                extractVsix(tempVsix, targetDir);
                Files.deleteIfExists(tempVsix);

                LOG.infof("Successfully provisioned %s LSP to %s", language, targetDir);
                return targetDir;

            } catch (Exception e) {
                LOG.errorf(e, "Failed to auto-provision LSP for %s", language);
                throw new RuntimeException("LSP provisioning failed: " + e.getMessage(), e);
            }
        });
    }

    private void extractVsix(Path vsixPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(vsixPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new SecurityException("VSIX entry attempts path traversal: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    if (resolved.getParent() != null) {
                        Files.createDirectories(resolved.getParent());
                    }
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
