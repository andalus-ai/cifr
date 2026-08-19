package tech.kayys.aljabr.engine.skill.review;

import java.util.List;

public record ReviewResult(
    ReviewStatus status,
    List<ReviewFinding> findings,
    List<String> strengths
) {
    public ReviewResult {
        findings = findings != null ? List.copyOf(findings) : List.of();
        strengths = strengths != null ? List.copyOf(strengths) : List.of();
    }

    public static ReviewResult pass(List<String> strengths) {
        return new ReviewResult(ReviewStatus.PASS, List.of(), strengths);
    }

    public static ReviewResult reject(List<ReviewFinding> findings) {
        return new ReviewResult(ReviewStatus.REJECT, findings, List.of());
    }

    public static ReviewResult warn(List<ReviewFinding> findings, List<String> strengths) {
        return new ReviewResult(ReviewStatus.WARN, findings, strengths);
    }
}
