package tech.kayys.aljabr.engine.tool.middleware;
import java.util.Set;
import tech.kayys.aljabr.engine.tool.protocol.ToolAuthority;
public record ToolContext(
    String taskId,
    String stepId,
    String phase,
    Set<ToolAuthority> grantedAuthorities,
    String workspaceFingerprint,
    long timeoutMs
) {
    public ToolContext { grantedAuthorities = grantedAuthorities != null ? Set.copyOf(grantedAuthorities) : Set.of(); }
    public boolean hasAuthority(ToolAuthority authority) { return grantedAuthorities.contains(authority); }
    public static ToolContext readOnly(String taskId, String phase) {
        return new ToolContext(taskId, null, phase, Set.of(ToolAuthority.READ, ToolAuthority.ANALYZE), null, 30_000L);
    }
}
