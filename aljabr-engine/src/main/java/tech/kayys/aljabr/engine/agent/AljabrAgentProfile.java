package tech.kayys.aljabr.engine.agent;

import tech.kayys.andalus.spi.agent.AgentProfile;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Standard AgentProfile configurations for Aljabr Autonomous Coder
 * aligned with Andalus Agent Operating Model.
 */
public final class AljabrAgentProfile {

    public static final String PROFILE_ID = "aljabr-coding";
    public static final String PROFILE_NAME = "Aljabr Autonomous Coding Profile";

    private AljabrAgentProfile() {}

    public static AgentProfile defaults() {
        return new AgentProfile(
                PROFILE_ID,
                PROFILE_NAME,
                Set.of("coding", "filesystem", "shell", "git", "testing", "debugging"),
                Map.of(
                        "sandbox.enabled", true,
                        "git.auto_stash", true,
                        "verification.required", true
                ),
                new AgentProfile.ExecutionLimits(
                        Duration.ofMinutes(15),
                        50,
                        100,
                        300_000,
                        100_000
                ),
                new AgentProfile.ModelPolicy(
                        "coding-specialized",
                        null,
                        Set.of()
                ),
                new AgentProfile.ResourcePolicy(
                        true,
                        true,
                        true,
                        true
                ),
                new AgentProfile.SecurityPolicy(
                        false,
                        true,
                        true
                ),
                Map.of(
                        "tier", "community",
                        "environment", "local"
                )
        );
    }
}
