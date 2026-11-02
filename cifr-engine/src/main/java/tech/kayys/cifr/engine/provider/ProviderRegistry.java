package tech.kayys.cifr.engine.provider;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.andalus.inference.InferenceProvider;
import tech.kayys.andalus.inference.ModelInfo;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Central registry of all {@link InferenceProvider} implementations available at runtime.
 *
 * <p>Supports both delivery models:
 * <ul>
 *   <li><strong>Option A (Built-in / Fat-JAR CDI):</strong> Auto-discovers all {@link InferenceProvider}
 *       beans on the compile-time classpath via CDI {@code @Any Instance<InferenceProvider>}.</li>
 *   <li><strong>Option B (Dynamic Plugin Loading):</strong> Scans {@code ~/.andalus/plugins/} (and
 *       optionally {@code ANDALUS_PLUGINS_DIR}) for external plugin JAR files at startup, loading them
 *       via isolated classloader and {@link ServiceLoader} / manifest discovery without requiring recompilation.</li>
 * </ul>
 */
@ApplicationScoped
public class ProviderRegistry {

    private static final Logger LOG = Logger.getLogger(ProviderRegistry.class);

    @Inject
    @Any
    Instance<InferenceProvider> builtInProviders;

    private final List<InferenceProvider> providers = new ArrayList<>();
    private final Set<String> registeredKeys = new HashSet<>();

    @PostConstruct
    void init() {
        // 1. Option A: Load built-in CDI providers
        loadBuiltInProviders();

        // 2. Option B: Dynamically load external plugin JARs from ~/.andalus/plugins
        loadDynamicPlugins();

        LOG.infof("ProviderRegistry initialised with %d total provider(s): %s",
                providers.size(), providers.stream().map(ProviderRegistry::resolveKey).toList());
    }

    private void loadBuiltInProviders() {
        if (builtInProviders == null) return;
        for (InferenceProvider provider : builtInProviders) {
            if (provider.getClass().getSimpleName().contains("DefaultInferenceBackend")) {
                continue; // Internal routing engine, not a distinct model vendor
            }
            registerProvider(provider, "built-in (CDI)");
        }
    }

    private void loadDynamicPlugins() {
        String pluginsDirEnv = System.getenv("ANDALUS_PLUGINS_DIR");
        Path pluginsDir = pluginsDirEnv != null && !pluginsDirEnv.isBlank()
                ? Paths.get(pluginsDirEnv)
                : Paths.get(System.getProperty("user.home"), ".andalus", "plugins");

        if (!Files.isDirectory(pluginsDir)) {
            LOG.debugf("Dynamic plugins directory does not exist: %s", pluginsDir);
            return;
        }

        try {
            File[] jarFiles = pluginsDir.toFile().listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) return;

            List<URL> urls = new ArrayList<>();
            for (File jar : jarFiles) {
                urls.add(jar.toURI().toURL());
            }

            URLClassLoader pluginClassLoader = new URLClassLoader(
                    urls.toArray(new URL[0]),
                    Thread.currentThread().getContextClassLoader()
            );

            // 1. ServiceLoader discovery
            ServiceLoader<InferenceProvider> loader = ServiceLoader.load(InferenceProvider.class, pluginClassLoader);
            for (InferenceProvider p : loader) {
                registerProvider(p, "dynamic ServiceLoader (" + pluginsDir + ")");
            }

            // 2. Manifest Plugin-Class discovery for jars specifying Plugin-Class attribute
            for (File jar : jarFiles) {
                try (JarFile jf = new JarFile(jar)) {
                    Manifest mf = jf.getManifest();
                    if (mf != null) {
                        String pluginClass = mf.getMainAttributes().getValue("Plugin-Class");
                        if (pluginClass != null && !pluginClass.isBlank()) {
                            try {
                                Class<?> clazz = Class.forName(pluginClass.trim(), true, pluginClassLoader);
                                if (InferenceProvider.class.isAssignableFrom(clazz)) {
                                    InferenceProvider inst = (InferenceProvider) clazz.getDeclaredConstructor().newInstance();
                                    registerProvider(inst, "manifest Plugin-Class (" + jar.getName() + ")");
                                }
                            } catch (Exception ex) {
                                LOG.warnf("Failed to instantiate Plugin-Class '%s' from %s: %s",
                                        pluginClass, jar.getName(), ex.getMessage());
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.warnf("Could not inspect manifest of jar %s: %s", jar.getName(), ex.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.warnf("Error scanning dynamic plugins directory %s: %s", pluginsDir, e.getMessage());
        }
    }

    private synchronized void registerProvider(InferenceProvider provider, String source) {
        String key = resolveKey(provider);
        if (registeredKeys.contains(key)) {
            LOG.debugf("Provider key '%s' already registered, skipping duplicate from %s", key, source);
            return;
        }

        try {
            provider.initialize();
            providers.add(provider);
            registeredKeys.add(key);
            LOG.infof("Registered inference provider [%s] from %s (class=%s)",
                    key, source, provider.getClass().getName());
        } catch (Exception e) {
            LOG.warnf("Provider '%s' (%s) failed to initialise and will be skipped: %s",
                    key, source, e.getMessage());
        }
    }

    /**
     * Returns all registered providers in load order.
     */
    public List<InferenceProvider> all() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * Finds a provider by its string key, name, or class name (case-insensitive, e.g. "gemini", "openai", "gollek").
     */
    public Optional<InferenceProvider> find(String providerId) {
        if (providerId == null || providerId.isBlank()) return Optional.empty();
        String target = providerId.trim().toLowerCase();
        return providers.stream()
                .filter(p -> matches(p, target))
                .findFirst();
    }

    /**
     * Returns a flat list of {@link ProviderDescriptor} records for gRPC / REST serialisation.
     */
    public List<ProviderDescriptor> describe() {
        List<ProviderDescriptor> result = new ArrayList<>();
        for (InferenceProvider p : providers) {
            String key = resolveKey(p);
            String name = p.metadata() != null && p.metadata().name() != null ? p.metadata().name() : key;
            String description = p.metadata() != null && p.metadata().description() != null ? p.metadata().description() : "";
            Set<String> models = safeListModels(p);
            result.add(new ProviderDescriptor(key, name, description, new ArrayList<>(models)));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the model list for a specific provider.
     */
    public List<ModelInfo> listModels(String providerId) {
        return find(providerId).map(p -> {
            try {
                List<ModelInfo> infos = new ArrayList<>();
                for (String modelId : p.listModels()) {
                    infos.add(p.getModelInfo(modelId));
                }
                return infos;
            } catch (Exception e) {
                LOG.warnf("Could not list models for provider '%s': %s", providerId, e.getMessage());
                return Collections.<ModelInfo>emptyList();
            }
        }).orElse(Collections.emptyList());
    }

    private boolean matches(InferenceProvider p, String target) {
        String key = resolveKey(p);
        if (key.equalsIgnoreCase(target)) return true;
        if (p.metadata() != null && p.metadata().name() != null && p.metadata().name().equalsIgnoreCase(target)) return true;
        if (p.id() != null && p.id().asString().equalsIgnoreCase(target)) return true;
        return p.getClass().getSimpleName().toLowerCase().contains(target);
    }

    public static String resolveKey(InferenceProvider p) {
        String cls = p.getClass().getSimpleName().toLowerCase();
        if (cls.contains("gemini")) return "gemini";
        if (cls.contains("openai")) return "openai";
        if (cls.contains("anthropic") || cls.contains("claude")) return "anthropic";
        if (cls.contains("mistral")) return "mistral";
        if (cls.contains("cerebras")) return "cerebras";
        if (cls.contains("gollek")) return "gollek";
        if (p.metadata() != null && p.metadata().name() != null) {
            return p.metadata().name().toLowerCase().replaceAll("\\s+", "-");
        }
        return p.id() != null ? p.id().asString() : "unknown";
    }

    private Set<String> safeListModels(InferenceProvider p) {
        try {
            Set<String> models = p.listModels();
            return models != null ? models : Set.of();
        } catch (Exception e) {
            LOG.warnf("listModels() failed for provider '%s': %s", p.getClass().getSimpleName(), e.getMessage());
            return Set.of();
        }
    }
}
