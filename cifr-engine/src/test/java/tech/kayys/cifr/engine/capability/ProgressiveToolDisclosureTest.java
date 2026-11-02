package tech.kayys.cifr.engine.capability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ProgressiveToolDisclosureTest {

    private DefaultCapabilityRegistry registry;
    private DefaultProgressiveToolResolver resolver;
    private DefaultCapabilityAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        registry = new DefaultCapabilityRegistry();
        resolver = new DefaultProgressiveToolResolver();
        resolver.capabilityRegistry = registry;

        authorizer = new DefaultCapabilityAuthorizer();
        authorizer.capabilityRegistry = registry;

        registry.register(Capability.of("file.read", "Read File", CapabilityCategory.READ, CapabilityRisk.SAFE));
        registry.register(Capability.of("build.compile", "Compile Project", CapabilityCategory.EXECUTE, CapabilityRisk.MEDIUM));
        registry.register(Capability.of("system.reformat_disk", "Format Disk", CapabilityCategory.SYSTEM, CapabilityRisk.CRITICAL));
    }

    @Test
    void testProgressiveSchemaRenderingAcrossLevels() {
        Optional<Capability> capOpt = registry.find("file.read");
        assertTrue(capOpt.isPresent());
        Capability cap = capOpt.get();

        String l0 = resolver.renderForLevel(cap, CapabilityLevel.L0_INDEX);
        assertTrue(l0.contains("file.read"));
        assertTrue(l0.contains("READ"));
        assertFalse(l0.contains("Inputs:"));

        String l1 = resolver.renderForLevel(cap, CapabilityLevel.L1_COMPACT);
        assertTrue(l1.contains("Read File"));
        assertTrue(l1.contains("Category: READ"));

        String l2 = resolver.renderForLevel(cap, CapabilityLevel.L2_FULL_SCHEMA);
        assertTrue(l2.contains("### Tool: Read File"));
        assertTrue(l2.contains("- Inputs:"));
        assertTrue(l2.contains("- Preconditions:"));
    }

    @Test
    void testCapabilityAuthorizationPolicyGating() {
        ToolExecutionContext ctx = ToolExecutionContext.of("exec-1", "/workspace");

        // SAFE capability allowed automatically
        CapabilityRequest safeReq = CapabilityRequest.fromModel("file.read", "Reading source file", Map.of());
        AuthorizationResult safeResult = authorizer.authorize(safeReq, ctx).await().atMost(Duration.ofSeconds(2));
        assertTrue(safeResult.authorized());
        assertFalse(safeResult.confirmationRequired());

        // CRITICAL capability requires explicit confirmation
        CapabilityRequest critReq = CapabilityRequest.fromModel("system.reformat_disk", "Clean disk", Map.of());
        AuthorizationResult critResult = authorizer.authorize(critReq, ctx).await().atMost(Duration.ofSeconds(2));
        assertTrue(critResult.authorized());
        assertTrue(critResult.confirmationRequired());
    }

    @Test
    void testCancellationTokenEnforcement() {
        ToolExecutionContext ctx = ToolExecutionContext.of("exec-2", "/workspace");
        ctx.cancellationToken().cancel("User aborted the execution");

        CapabilityRequest req = CapabilityRequest.fromModel("file.read", "Reading file", Map.of());
        AuthorizationResult result = authorizer.authorize(req, ctx).await().atMost(Duration.ofSeconds(2));

        assertFalse(result.authorized());
        assertTrue(result.reason().contains("Execution cancelled"));
    }

    @Test
    void testTaskCapabilitySelection() {
        List<Capability> selected = resolver.select("compile maven project and run build", List.of(), CapabilityLevel.L1_COMPACT)
            .await().atMost(Duration.ofSeconds(2));

        boolean hasCompile = selected.stream().anyMatch(c -> c.id().equals("build.compile"));
        assertTrue(hasCompile);
    }
}
