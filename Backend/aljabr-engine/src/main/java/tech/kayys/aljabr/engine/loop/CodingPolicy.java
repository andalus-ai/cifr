package tech.kayys.aljabr.engine.loop;

public record CodingPolicy(
    boolean requireApprovalForEdits,
    boolean requireApprovalForCommands,
    int maxRepairIterations
) {
    public static CodingPolicy defaultPolicy() {
        // As per user feedback, backend auto-approves for now in this phase
        return new CodingPolicy(false, false, 3);
    }
}
