package tech.kayys.cifr.engine.capability;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default progressive tool disclosure engine and capability selector.
 */
@ApplicationScoped
public class DefaultProgressiveToolResolver implements ProgressiveToolDescriptor, CapabilitySelector {

    @Inject
    public CapabilityRegistry capabilityRegistry;

    public DefaultProgressiveToolResolver() {}

    public DefaultProgressiveToolResolver(CapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
    }

    @Override
    public String renderForLevel(Capability capability, CapabilityLevel level) {
        if (capability == null) {
            return "";
        }

        CapabilityLevel disclosureLevel = level != null ? level : CapabilityLevel.L1_COMPACT;

        return switch (disclosureLevel) {
            case L0_INDEX -> String.format("- %s [%s] (%s)", capability.id(), capability.category(), capability.risk());
            case L1_COMPACT -> String.format("- %s: %s (Category: %s, Risk: %s, Idempotency: %s)",
                capability.id(),
                capability.name(),
                capability.category(),
                capability.risk(),
                capability.contract() != null ? capability.contract().idempotency() : "UNKNOWN"
            );
            case L2_FULL_SCHEMA -> {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("### Tool: %s (%s)\n", capability.name(), capability.id()));
                sb.append(String.format("- Category: %s | Risk: %s\n", capability.category(), capability.risk()));
                if (capability.contract() != null) {
                    sb.append(String.format("- Inputs: %s\n", capability.contract().inputSchema()));
                    sb.append(String.format("- Outputs: %s\n", capability.contract().outputSchema()));
                    sb.append(String.format("- Preconditions: %s\n", capability.contract().preconditions()));
                    sb.append(String.format("- Postconditions: %s\n", capability.contract().postconditions()));
                }
                yield sb.toString();
            }
        };
    }

    @Override
    public Uni<List<Capability>> select(String taskDescription, List<String> activeSkillIds, CapabilityLevel level) {
        return Uni.createFrom().item(() -> {
            List<Capability> all = capabilityRegistry != null ? capabilityRegistry.list() : List.of();
            if (taskDescription == null || taskDescription.isBlank()) {
                return all;
            }

            String lower = taskDescription.toLowerCase();
            String[] words = lower.split("[\\s._-]+");

            // Match capabilities whose name, ID, or category relates to task keywords
            List<Capability> filtered = all.stream()
                .filter(c -> {
                    if (activeSkillIds != null && activeSkillIds.contains(c.id())) {
                        return true;
                    }
                    String cId = c.id().toLowerCase();
                    String cName = c.name().toLowerCase();
                    String cCat = c.category().name().toLowerCase();
                    return Arrays.stream(words)
                        .filter(w -> w.length() >= 3)
                        .anyMatch(w -> cId.contains(w) || cName.contains(w) || cCat.contains(w));
                })
                .collect(Collectors.toList());

            // If nothing matched specifically, return standard core read/execute capabilities
            if (filtered.isEmpty()) {
                return all.stream()
                    .filter(c -> c.risk() == CapabilityRisk.SAFE || c.category() == CapabilityCategory.READ)
                    .collect(Collectors.toList());
            }

            return filtered;
        });
    }
}
